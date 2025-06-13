# from flask import Flask, request, jsonify
# import requests
# import logging
# from datetime import datetime
# import pandas as pd
# import numpy as np
# from sklearn.ensemble import IsolationForest
# from sklearn.preprocessing import StandardScaler
# from sklearn.impute import SimpleImputer

# app = Flask(__name__)
# logging.basicConfig(level=logging.INFO)
# logger = logging.getLogger(__name__)

# PROMETHEUS_URL = 'http://prometheus:9090'

# # Initialize and fit models
# def initialize_models():
#     try:
#         model = IsolationForest(contamination=0.1, random_state=42)
#         scaler = StandardScaler()

#         # Sample metrics for fitting
#         feature_names = ['latency', 'error_rate', 'throughput', 'cpu_usage', 'queue_length']
#         sample_metrics = [
#             [100.0, 0.01, 10.0, 0.5, 5.0],  # Normal case
#             [500.0, 0.1, 5.0, 0.9, 10.0],   # High latency/error
#             [50.0, 0.0, 20.0, 0.2, 2.0]     # Low latency
#         ]
#         sample_df = pd.DataFrame(sample_metrics, columns=feature_names)

#         # Impute NaN (if any) in sample data
#         imputer = SimpleImputer(strategy='constant', fill_value=0)
#         sample_imputed = imputer.fit_transform(sample_df)

#         # Fit scaler
#         scaler.fit(sample_imputed)

#         # Fit IsolationForest
#         model.fit(sample_imputed)

#         logger.info("Initialized and fitted IsolationForest and StandardScaler")
#         return model, scaler
#     except Exception as e:
#         logger.error(f"Error initializing models: {str(e)}")
#         raise

# isolation_forest_model, scaler = initialize_models()

# def fetch_prometheus_metric(query):
#     try:
#         response = requests.get(f'{PROMETHEUS_URL}/api/v1/query', params={'query': query})
#         response.raise_for_status()
#         result = response.json()['data']['result']
#         return float(result[0]['value'][1]) if result else 0.0
#     except Exception as e:
#         logger.warning(f"Failed to fetch metric for query {query}: {str(e)}")
#         return 0.0

# def hybrid_anomaly_detection(model, scaler, metrics):
#     try:
#         # Define feature names
#         feature_names = ['latency', 'error_rate', 'throughput', 'cpu_usage', 'queue_length']

#         # Convert metrics to DataFrame
#         metrics_df = pd.DataFrame([metrics], columns=feature_names)

#         # Impute NaN values with 0
#         imputer = SimpleImputer(strategy='constant', fill_value=0)
#         metrics_imputed = imputer.fit_transform(metrics_df)

#         # Scale features
#         scaled_features = scaler.transform(metrics_imputed)

#         # Predict anomaly
#         prediction = model.predict(scaled_features)
#         is_anomaly = prediction[0] == -1  # -1 indicates anomaly in IsolationForest

#         # Rule-based checks
#         reason = ""
#         if metrics['latency'] > 200:
#             is_anomaly = True
#             reason = f"High latency: {metrics['latency']:.2f}ms > 200ms"
#         elif metrics['error_rate'] > 0.05:
#             is_anomaly = True
#             reason = f"High error rate: {metrics['error_rate']:.2%} > 5%"
#         elif metrics['cpu_usage'] > 0.8:
#             is_anomaly = True
#             reason = f"High CPU usage: {metrics['cpu_usage']:.2f} > 0.8"

#         logger.info(f"Anomaly prediction: is_anomaly={is_anomaly}, reason={reason}")
#         return bool(is_anomaly), reason  # Convert NumPy bool_ to Python bool
#     except Exception as e:
#         logger.error(f"Error in hybrid_anomaly_detection: {str(e)}")
#         raise

# @app.route('/detect-anomaly', methods=['POST'])
# def detect_anomaly():
#     data = request.json
#     endpoint = data.get('endpoint')
#     alertname = data.get('alertname')
#     logger.info(f"Received anomaly detection request for {endpoint}, alert: {alertname}")

#     try:
#         # Fetch metrics
#         latency_query = f'avg(rate(http_server_requests_seconds_sum{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}[5m]) / rate(http_server_requests_seconds_count{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}[5m])) * 1000'
#         error_rate_query = f'rate(http_server_requests_seconds_count{{uri="/api/{endpoint}", status=~"5..", application="autonomous-api-optimizer"}}[5m]) / rate(http_server_requests_seconds_count{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}[5m])'
#         throughput_query = f'rate(http_server_requests_seconds_count{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}[5m])'
#         cpu_usage_query = f'rate(process_cpu_seconds_total{{job="spring-boot", application="autonomous-api-optimizer"}}[5m])'
#         queue_length_query = f'http_server_requests_seconds_count{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}'

#         metrics = {
#             'latency': fetch_prometheus_metric(latency_query),
#             'error_rate': fetch_prometheus_metric(error_rate_query),
#             'throughput': fetch_prometheus_metric(throughput_query),
#             'cpu_usage': fetch_prometheus_metric(cpu_usage_query),
#             'queue_length': fetch_prometheus_metric(queue_length_query)
#         }

#         # Detect anomaly
#         is_anomaly, reason = hybrid_anomaly_detection(isolation_forest_model, scaler, metrics)

#         result = {
#             'endpoint_path': f'/api/{endpoint}',
#             'is_anomaly': is_anomaly,
#             'reason': reason,
#             'metrics': {
#                 'latency': metrics['latency'],
#                 'error_rate': metrics['error_rate'],
#                 'throughput': metrics['throughput'],
#                 'cpu_usage': metrics['cpu_usage'],
#                 'queue_length': metrics['queue_length'],
#                 'throughput_mean': metrics['throughput']  # Simplified
#             },
#             'timestamp': datetime.utcnow().isoformat()
#         }
#         logger.info(f"Anomaly detection result for /api/{endpoint}: {result}")
#         return jsonify(result)
#     except Exception as e:
#         logger.error(f"Error in anomaly detection for {endpoint}: {str(e)}")
#         return jsonify({'error': str(e)}), 500

# if __name__ == '__main__':
#     app.run(host='0.0.0.0', port=5000)
# app.py

from flask import Flask, request, jsonify
import logging
import requests
from datetime import datetime
import pandas as pd
import joblib
from sklearn.impute import SimpleImputer

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load model and scaler
model = joblib.load('model.pkl')
scaler = joblib.load('scaler.pkl')
imputer = SimpleImputer(strategy='constant', fill_value=0)

PROMETHEUS_URL = 'http://prometheus:9090'

def fetch_prometheus_metric(query):
    try:
        response = requests.get(f'{PROMETHEUS_URL}/api/v1/query', params={'query': query})
        response.raise_for_status()
        result = response.json()['data']['result']
        return float(result[0]['value'][1]) if result else 0.0
    except Exception as e:
        logger.warning(f"Failed to fetch metric for query {query}: {str(e)}")
        return 0.0

@app.route('/detect-anomaly', methods=['POST'])
def detect_anomaly():
    data = request.json
    endpoint = data.get('endpoint', 'process')

    # Define Prometheus queries
    queries = {
        'latency': f'avg(rate(http_server_requests_seconds_sum{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}[5m]) / rate(http_server_requests_seconds_count{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}[5m])) * 1000',
        'error_rate': f'rate(http_server_requests_seconds_count{{uri="/api/{endpoint}", status=~"5..", application="autonomous-api-optimizer"}}[5m]) / rate(http_server_requests_seconds_count{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}[5m])',
        'throughput': f'rate(http_server_requests_seconds_count{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}[5m])',
        'cpu_usage': f'rate(process_cpu_seconds_total{{job="spring-boot", application="autonomous-api-optimizer"}}[5m])',
        'queue_length': f'http_server_requests_seconds_count{{uri="/api/{endpoint}", application="autonomous-api-optimizer"}}'
    }

    # Fetch metrics
    metrics = {key: fetch_prometheus_metric(query) for key, query in queries.items()}
    logger.info(f"Fetched metrics: {metrics}")

    try:
        metrics_df = pd.DataFrame([metrics])
        imputed = imputer.fit_transform(metrics_df)
        scaled = scaler.transform(imputed)
        is_anomaly = model.predict(scaled)[0] == -1

        result = {
            'endpoint_path': f'/api/{endpoint}',
            'is_anomaly': bool(is_anomaly),
            'metrics': metrics,
            'timestamp': datetime.utcnow().isoformat()
        }
        logger.info(f"Anomaly detection result: {result}")
        return jsonify(result)
    except Exception as e:
        logger.error(f"Detection failed: {str(e)}")
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
