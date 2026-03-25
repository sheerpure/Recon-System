import csv
import random

# Configuration
output_file = 'paysim_test_30x.csv'
base_rows = 150  # 5 rows * 30 times
types = ['PAYMENT', 'TRANSFER', 'CASH_OUT', 'DEBIT', 'CASH_IN']

header = [
    "step", "type", "amount", "nameOrig", "oldbalanceOrg", 
    "newbalanceOrig", "nameDest", "oldbalanceDest", "newbalanceDest", 
    "isFraud", "isFlaggedFraud"
]

def generate_data(num_rows):
    data = []
    for i in range(num_rows):
        step = random.randint(1, 10)
        tx_type = random.choice(types)
        amount = round(random.uniform(100, 500000), 2)
        
        # Original account logic
        old_org = round(random.uniform(amount, amount + 100000), 2)
        new_org = round(old_org - amount, 2)
        
        # Destination account logic
        old_dest = round(random.uniform(0, 50000), 2)
        new_dest = round(old_dest + amount, 2)
        
        # Fraud logic: higher probability if amount is very large
        is_fraud = 1 if (tx_type == 'TRANSFER' and amount > 200000 and random.random() > 0.5) else 0
        is_flagged = 1 if (amount > 200000 and is_fraud == 1) else 0
        
        row = [
            step, tx_type, amount, f"C{1000+i}", old_org, new_org,
            f"M{2000+i}" if tx_type == 'PAYMENT' else f"C{3000+i}",
            old_dest, new_dest, is_fraud, is_flagged
        ]
        data.append(row)
    return data

# Write to CSV
with open(output_file, 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(header)
    writer.writerows(generate_data(base_rows))

print(f"✅ Successfully generated {output_file} with {base_rows} rows.")