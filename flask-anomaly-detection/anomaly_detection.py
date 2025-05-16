from flask import Flask, request, jsonify, Response
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
import logging
import pandas as pd
from datetime import datetime, timedelta
import requests
from prometheus_client import Counter, generate_latest

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Prometheus counter for anomalies
anomaly_counter = Counter('anomalies_detected_total', 'Total anomalies detected')

# Fetch metrics from Prometheus
def fetch_prometheus_metrics():
    try:
        url = "http://prometheus:9090/api/v1/query"
        queries = {
            'latency': 'avg(rate(http_request_duration_seconds_sum[5m])/rate(http_request_duration_seconds_count[5m]))*1000',
            'throughput': 'rate(http_requests_total[5m])',
            'error_rate': 'rate(http_requests_total{status=~"5.."}[5m])/rate(http_requests_total[5m])*100',
            'cpu_usage': 'rate(jvm_cpu_seconds_total[5m])*100',
            'queue_length': 'sum(spring_task_executor_queue_size)'  # Example for queue length
        }
        metrics = {}
        for key, query in queries.items():
            response = requests.get(url, params={'query': query}, timeout=5)
            if response.status_code == 200 and response.json()['data']['result']:
                metrics[key] = float(response.json()['data']['result'][0]['value'][1])
            else:
                metrics[key] = 0  # Fallback for missing data
                logger.warning(f"Failed to fetch {key} from Prometheus")
        metrics['throughput_mean'] = metrics.get('throughput', 0)  # Approximate mean
        return metrics
    except Exception as e:
        logger.error(f"Error fetching Prometheus metrics: {str(e)}")
        return None

# Train Isolation Forest model with mock data (for initial training)
def generate_mock_data(n_samples=10000):
    np.random.seed(42)
    timestamps = [datetime.now() - timedelta(minutes=i) for i in range(n_samples)]
    timestamps.reverse()

    base_latency = np.random.lognormal(mean=np.log(50), sigma=0.3, size=n_samples).clip(min=5)
    time_factor = np.sin(np.linspace(0, 4*np.pi, n_samples)) * 20 + 100
    throughput = np.random.normal(time_factor, 10, n_samples)
    error_rate = np.random.exponential(0.5, n_samples).clip(0, 5)
    cpu_usage = throughput * np.random.normal(0.7, 0.05, n_samples).clip(min=10)
    queue_length = np.random.exponential(2, n_samples).clip(0, 50)

    anomaly_indices = np.random.choice(n_samples, size=int(0.05 * n_samples), replace=False)
    base_latency[anomaly_indices] = np.random.normal(400, 100, len(anomaly_indices)).clip(min=100)
    error_rate[anomaly_indices] = np.random.normal(10, 2, len(anomaly_indices)).clip(min=5)
    throughput[anomaly_indices] *= np.random.uniform(0.4, 0.8, len(anomaly_indices))
    cpu_usage[anomaly_indices] *= np.random.uniform(1.3, 1.8, len(anomaly_indices)).clip(max=100)
    queue_length[anomaly_indices] = np.random.normal(100, 20, len(anomaly_indices)).clip(min=50)

    error_rate += base_latency * 0.01

    return pd.DataFrame({
        'timestamp': timestamps,
        'latency': base_latency,
        'throughput': throughput,
        'error_rate': error_rate,
        'cpu_usage': cpu_usage,
        'queue_length': queue_length
    })

# Train Isolation Forest model
def train_isolation_forest(data):
    features = data[['latency', 'throughput', 'error_rate', 'cpu_usage', 'queue_length']]
    scaler = StandardScaler()
    scaled_features = scaler.fit_transform(features)

    model = IsolationForest(
        n_estimators=200,
        contamination=0.05,
        max_samples=512,
        random_state=42,
        n_jobs=-1
    )
    model.fit(scaled_features)
    logger.info("Isolation Forest model trained successfully")
    return model, scaler

# Threshold-based anomaly detection
def threshold_anomaly_detection(data_point):
    thresholds = {
        'latency': 200,
        'error_rate': 5,
        'throughput_drop': 0.5,
        'cpu_usage': 85,
        'queue_length': 50
    }

    reasons = []
    if data_point['latency'] > thresholds['latency']:
        reasons.append(f"High latency: {data_point['latency']:.2f}ms > {thresholds['latency']}ms")
    if data_point['error_rate'] > thresholds['error_rate']:
        reasons.append(f"High error rate: {data_point['error_rate']:.2f}% > {thresholds['error_rate']}%")
    if data_point['throughput'] < data_point['throughput_mean'] * thresholds['throughput_drop']:
        reasons.append(f"Low throughput: {data_point['throughput']:.2f} < {thresholds['throughput_drop']*100}% of mean")
    if data_point['cpu_usage'] > thresholds['cpu_usage']:
        reasons.append(f"High CPU usage: {data_point['cpu_usage']:.2f}% > {thresholds['cpu_usage']}%")
    if data_point['queue_length'] > thresholds['queue_length']:
        reasons.append(f"High queue length: {data_point['queue_length']:.2f} > {thresholds['queue_length']}")

    return bool(reasons), "; ".join(reasons) if reasons else None

# Hybrid anomaly detection
def hybrid_anomaly_detection(model, scaler, data_point):
    latency_threshold = 200
    if data_point['latency'] > latency_threshold:
        is_anomaly, threshold_reason = threshold_anomaly_detection(data_point)
        if is_anomaly:
            return True, threshold_reason
        return False, "High latency but within other thresholds"

    features = np.array([[
        data_point['latency'],
        data_point['throughput'],
        data_point['error_rate'],
        data_point['cpu_usage'],
        data_point['queue_length']
    ]])
    scaled_features = scaler.transform(features)
    prediction = model.predict(scaled_features)
    is_anomaly = prediction[0] == -1
    reason = "Isolation Forest ML detection" if is_anomaly else "Normal"

    return is_anomaly, reason

# Initialize model
mock_data = generate_mock_data()
isolation_forest_model, scaler = train_isolation_forest(mock_data)

@app.route('/detect-anomaly', methods=['POST'])
def detect_anomaly():
    try:
        # Try to fetch metrics from Prometheus first
        metrics = fetch_prometheus_metrics()
        if metrics is None:
            return jsonify({'error': 'Failed to fetch metrics from Prometheus'}), 500

        is_anomaly, reason = hybrid_anomaly_detection(isolation_forest_model, scaler, metrics)
        if is_anomaly:
            anomaly_counter.inc()

        response = {
            'is_anomaly': is_anomaly,
            'reason': reason,
            'metrics': metrics,
            'timestamp': datetime.now().isoformat()
        }
        logger.info(f"Anomaly detection result: {response}")
        return jsonify(response), 200

    except Exception as e:
        logger.error(f"Error in anomaly detection: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/metrics')
def metrics():
    return Response(generate_latest(), mimetype='text/plain')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)