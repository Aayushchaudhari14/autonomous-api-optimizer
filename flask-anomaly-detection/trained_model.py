# train_model.py  – re‑train IsolationForest so any latency >150 ms is anomalous

import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer
import joblib

def generate_synthetic_data(n_samples=10_000, anomaly_ratio=0.1):
    """
    Create a dataset with 5 features. 10 % of the rows are anomalous.
    • Normal latency  ~ N(100, 20)  (≈ 60 % between 80–120 ms)
    • Anomalous latency-only rows are sampled 160–400 ms (all >150 ms)
    """
    np.random.seed(42)
    n_anom   = int(n_samples * anomaly_ratio)
    n_norm   = n_samples - n_anom
    n_each   = n_anom // 5            # split anomalies into 5 equal buckets

    # ----------  NORMAL  ----------
    normal = np.column_stack([
        np.random.normal(100, 20, n_norm),     # latency
        np.random.normal(0.01, 0.005, n_norm), # error_rate
        np.random.normal(20, 5, n_norm),       # throughput
        np.random.normal(0.4, 0.1, n_norm),    # cpu_usage
        np.random.normal(5, 2, n_norm)         # queue_length
    ])

    # ----------  ANOMALIES  ----------
    # 1️⃣  High‑latency‑only anomalies  (160–400 ms  → all >150 ms)
    anom_latency = np.column_stack([
        np.random.uniform(160, 400, n_each),              # latency ↑
        np.random.normal(0.01, 0.002, n_each),            # others ≈ normal
        np.random.normal(20, 3, n_each),
        np.random.normal(0.4, 0.05, n_each),
        np.random.normal(5, 1, n_each)
    ])

    # 2️⃣  High error‑rate‑only
    anom_error = np.column_stack([
        np.random.normal(100, 10, n_each),
        np.random.uniform(0.1, 0.4, n_each),              # error ↑
        np.random.normal(20, 3, n_each),
        np.random.normal(0.4, 0.05, n_each),
        np.random.normal(5, 1, n_each)
    ])

    # 3️⃣  High CPU‑only
    anom_cpu = np.column_stack([
        np.random.normal(100, 10, n_each),
        np.random.normal(0.01, 0.002, n_each),
        np.random.normal(20, 3, n_each),
        np.random.uniform(0.85, 1.0, n_each),             # CPU ↑
        np.random.normal(5, 1, n_each)
    ])

    # 4️⃣  Low throughput‑only
    anom_thr = np.column_stack([
        np.random.normal(100, 10, n_each),
        np.random.normal(0.01, 0.002, n_each),
        np.random.uniform(0, 5, n_each),                  # throughput ↓
        np.random.normal(0.4, 0.05, n_each),
        np.random.normal(5, 1, n_each)
    ])

    # 5️⃣  Mixed severe anomalies
    anom_mixed = np.column_stack([
        np.random.uniform(160, 400, n_each),              # latency ↑
        np.random.uniform(0.2, 0.5, n_each),              # error ↑
        np.random.uniform(0, 5, n_each),                  # throughput ↓
        np.random.uniform(0.9, 1.0, n_each),              # CPU ↑
        np.random.uniform(15, 30, n_each)                 # queue ↑
    ])

    # ----------  Assemble & shuffle ----------
    full = np.vstack([normal, anom_latency, anom_error, anom_cpu, anom_thr, anom_mixed])
    np.random.shuffle(full)          # random order
    columns = ['latency', 'error_rate', 'throughput', 'cpu_usage', 'queue_length']
    return pd.DataFrame(full, columns=columns)

# ---- Train IsolationForest on the synthetic dataset ----
df        = generate_synthetic_data()
imputer   = SimpleImputer(strategy='constant', fill_value=0)
X_imp     = imputer.fit_transform(df)
scaler    = StandardScaler()
X_scaled  = scaler.fit_transform(X_imp)

model = IsolationForest(contamination=0.1, random_state=42)
model.fit(X_scaled)

joblib.dump(model,  'model.pkl')
joblib.dump(scaler, 'scaler.pkl')
print("✅ Model and scaler saved successfully (latency>150 ms included as anomaly).")
