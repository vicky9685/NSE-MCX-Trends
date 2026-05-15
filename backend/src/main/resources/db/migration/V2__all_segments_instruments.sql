-- V2__all_segments_instruments.sql
-- Insert ALL NSE segments and instruments across all major sectors

-- ─── SECTOR: BANKING & FINANCE ───────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('HDFCBANK',   'HDFC Bank Ltd',                  'EQUITY', 'NSE', 1, 0.05),
('ICICIBANK',  'ICICI Bank Ltd',                 'EQUITY', 'NSE', 1, 0.05),
('KOTAKBANK',  'Kotak Mahindra Bank Ltd',         'EQUITY', 'NSE', 1, 0.05),
('AXISBANK',   'Axis Bank Ltd',                  'EQUITY', 'NSE', 1, 0.05),
('SBIN',       'State Bank of India',             'EQUITY', 'NSE', 1, 0.05),
('INDUSINDBK', 'IndusInd Bank Ltd',               'EQUITY', 'NSE', 1, 0.05),
('FEDERALBNK', 'Federal Bank Ltd',               'EQUITY', 'NSE', 1, 0.05),
('BANDHANBNK', 'Bandhan Bank Ltd',               'EQUITY', 'NSE', 1, 0.05),
('IDFCFIRSTB', 'IDFC First Bank Ltd',            'EQUITY', 'NSE', 1, 0.05),
('PNB',        'Punjab National Bank',            'EQUITY', 'NSE', 1, 0.05),
('BANKBARODA', 'Bank of Baroda',                 'EQUITY', 'NSE', 1, 0.05),
('CANBK',      'Canara Bank',                    'EQUITY', 'NSE', 1, 0.05),
('UNIONBANK',  'Union Bank of India',             'EQUITY', 'NSE', 1, 0.05),
('BAJFINANCE', 'Bajaj Finance Ltd',              'EQUITY', 'NSE', 1, 0.05),
('BAJAJFINSV', 'Bajaj Finserv Ltd',              'EQUITY', 'NSE', 1, 0.05),
('HDFCLIFE',   'HDFC Life Insurance',            'EQUITY', 'NSE', 1, 0.05),
('SBILIFE',    'SBI Life Insurance',             'EQUITY', 'NSE', 1, 0.05),
('ICICIGI',    'ICICI General Insurance',         'EQUITY', 'NSE', 1, 0.05),
('MUTHOOTFIN', 'Muthoot Finance Ltd',            'EQUITY', 'NSE', 1, 0.05),
('CHOLAFIN',   'Cholamandalam Investment',        'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: INFORMATION TECHNOLOGY ─────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('TCS',        'Tata Consultancy Services',       'EQUITY', 'NSE', 1, 0.05),
('INFY',       'Infosys Ltd',                    'EQUITY', 'NSE', 1, 0.05),
('WIPRO',      'Wipro Ltd',                      'EQUITY', 'NSE', 1, 0.05),
('HCLTECH',    'HCL Technologies Ltd',            'EQUITY', 'NSE', 1, 0.05),
('TECHM',      'Tech Mahindra Ltd',              'EQUITY', 'NSE', 1, 0.05),
('LTI',        'LTIMindtree Ltd',                'EQUITY', 'NSE', 1, 0.05),
('MPHASIS',    'Mphasis Ltd',                    'EQUITY', 'NSE', 1, 0.05),
('PERSISTENT', 'Persistent Systems Ltd',          'EQUITY', 'NSE', 1, 0.05),
('COFORGE',    'Coforge Ltd',                    'EQUITY', 'NSE', 1, 0.05),
('OFSS',       'Oracle Financial Services',       'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: OIL & GAS / ENERGY ──────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('RELIANCE',   'Reliance Industries Ltd',         'EQUITY', 'NSE', 1, 0.05),
('ONGC',       'Oil & Natural Gas Corp',          'EQUITY', 'NSE', 1, 0.05),
('IOC',        'Indian Oil Corporation',           'EQUITY', 'NSE', 1, 0.05),
('BPCL',       'Bharat Petroleum Corp',           'EQUITY', 'NSE', 1, 0.05),
('HINDPETRO',  'Hindustan Petroleum Corp',         'EQUITY', 'NSE', 1, 0.05),
('GAIL',       'GAIL India Ltd',                  'EQUITY', 'NSE', 1, 0.05),
('PETRONET',   'Petronet LNG Ltd',                'EQUITY', 'NSE', 1, 0.05),
('OIL',        'Oil India Ltd',                   'EQUITY', 'NSE', 1, 0.05),
('MGL',        'Mahanagar Gas Ltd',               'EQUITY', 'NSE', 1, 0.05),
('IGL',        'Indraprastha Gas Ltd',             'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: AUTOMOBILE ──────────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('MARUTI',     'Maruti Suzuki India Ltd',         'EQUITY', 'NSE', 1, 0.05),
('TATAMOTORS', 'Tata Motors Ltd',                 'EQUITY', 'NSE', 1, 0.05),
('M&M',        'Mahindra & Mahindra Ltd',          'EQUITY', 'NSE', 1, 0.05),
('BAJAJ-AUTO', 'Bajaj Auto Ltd',                  'EQUITY', 'NSE', 1, 0.05),
('HEROMOTOCO', 'Hero MotoCorp Ltd',               'EQUITY', 'NSE', 1, 0.05),
('EICHERMOT',  'Eicher Motors Ltd',               'EQUITY', 'NSE', 1, 0.05),
('TVSMOTOR',   'TVS Motor Company',               'EQUITY', 'NSE', 1, 0.05),
('ASHOKLEY',   'Ashok Leyland Ltd',               'EQUITY', 'NSE', 1, 0.05),
('BOSCHLTD',   'Bosch Ltd',                       'EQUITY', 'NSE', 1, 0.05),
('MOTHERSON',  'Samvardhana Motherson',            'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: PHARMACEUTICALS ─────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('SUNPHARMA',  'Sun Pharmaceutical Industries',   'EQUITY', 'NSE', 1, 0.05),
('DRREDDY',    'Dr. Reddys Laboratories',         'EQUITY', 'NSE', 1, 0.05),
('CIPLA',      'Cipla Ltd',                       'EQUITY', 'NSE', 1, 0.05),
('DIVISLAB',   'Divis Laboratories',              'EQUITY', 'NSE', 1, 0.05),
('BIOCON',     'Biocon Ltd',                      'EQUITY', 'NSE', 1, 0.05),
('LUPIN',      'Lupin Ltd',                       'EQUITY', 'NSE', 1, 0.05),
('AUROPHARMA', 'Aurobindo Pharma Ltd',            'EQUITY', 'NSE', 1, 0.05),
('TORNTPHARM', 'Torrent Pharmaceuticals',          'EQUITY', 'NSE', 1, 0.05),
('IPCALAB',    'IPCA Laboratories',               'EQUITY', 'NSE', 1, 0.05),
('ALKEM',      'Alkem Laboratories',              'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: FMCG / CONSUMER ─────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('HINDUNILVR', 'Hindustan Unilever Ltd',          'EQUITY', 'NSE', 1, 0.05),
('ITC',        'ITC Ltd',                         'EQUITY', 'NSE', 1, 0.05),
('NESTLEIND',  'Nestle India Ltd',                'EQUITY', 'NSE', 1, 0.05),
('BRITANNIA',  'Britannia Industries Ltd',         'EQUITY', 'NSE', 1, 0.05),
('DABUR',      'Dabur India Ltd',                 'EQUITY', 'NSE', 1, 0.05),
('MARICO',     'Marico Ltd',                      'EQUITY', 'NSE', 1, 0.05),
('COLPAL',     'Colgate-Palmolive India',          'EQUITY', 'NSE', 1, 0.05),
('EMAMILTD',   'Emami Ltd',                       'EQUITY', 'NSE', 1, 0.05),
('GODREJCP',   'Godrej Consumer Products',         'EQUITY', 'NSE', 1, 0.05),
('VBL',        'Varun Beverages Ltd',              'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: METALS & MINING ─────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('TATASTEEL',  'Tata Steel Ltd',                  'EQUITY', 'NSE', 1, 0.05),
('JSWSTEEL',   'JSW Steel Ltd',                   'EQUITY', 'NSE', 1, 0.05),
('HINDALCO',   'Hindalco Industries Ltd',          'EQUITY', 'NSE', 1, 0.05),
('VEDL',       'Vedanta Ltd',                     'EQUITY', 'NSE', 1, 0.05),
('COALINDIA',  'Coal India Ltd',                  'EQUITY', 'NSE', 1, 0.05),
('NMDC',       'NMDC Ltd',                        'EQUITY', 'NSE', 1, 0.05),
('SAIL',       'Steel Authority of India',         'EQUITY', 'NSE', 1, 0.05),
('NATIONALUM', 'National Aluminium Company',       'EQUITY', 'NSE', 1, 0.05),
('HINDCOPPER', 'Hindustan Copper Ltd',             'EQUITY', 'NSE', 1, 0.05),
('MOIL',       'MOIL Ltd',                        'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: INFRASTRUCTURE / CAPITAL GOODS ──────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('LT',          'Larsen & Toubro Ltd',            'EQUITY', 'NSE', 1, 0.05),
('ADANIPORTS',  'Adani Ports & SEZ',              'EQUITY', 'NSE', 1, 0.05),
('POWERGRID',   'Power Grid Corporation',          'EQUITY', 'NSE', 1, 0.05),
('NTPC',        'NTPC Ltd',                       'EQUITY', 'NSE', 1, 0.05),
('ADANIENT',    'Adani Enterprises',              'EQUITY', 'NSE', 1, 0.05),
('ADANIGREEN',  'Adani Green Energy',             'EQUITY', 'NSE', 1, 0.05),
('SIEMENS',     'Siemens Ltd',                    'EQUITY', 'NSE', 1, 0.05),
('ABB',         'ABB India Ltd',                  'EQUITY', 'NSE', 1, 0.05),
('BEL',         'Bharat Electronics Ltd',          'EQUITY', 'NSE', 1, 0.05),
('HAL',         'Hindustan Aeronautics Ltd',       'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: TELECOM & MEDIA ─────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('BHARTIARTL', 'Bharti Airtel Ltd',               'EQUITY', 'NSE', 1, 0.05),
('IDEA',       'Vodafone Idea Ltd',               'EQUITY', 'NSE', 1, 0.05),
('TATACOMM',   'Tata Communications',             'EQUITY', 'NSE', 1, 0.05),
('ZEEL',       'Zee Entertainment Enterprises',   'EQUITY', 'NSE', 1, 0.05),
('SUNTV',      'Sun TV Network',                  'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: REAL ESTATE ─────────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('DLF',        'DLF Ltd',                         'EQUITY', 'NSE', 1, 0.05),
('GODREJPROP', 'Godrej Properties',               'EQUITY', 'NSE', 1, 0.05),
('PRESTIGE',   'Prestige Estates Projects',        'EQUITY', 'NSE', 1, 0.05),
('OBEROIRLTY', 'Oberoi Realty Ltd',               'EQUITY', 'NSE', 1, 0.05),
('BRIGADE',    'Brigade Enterprises',             'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: HEALTHCARE / HOSPITALS ──────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('APOLLOHOSP', 'Apollo Hospitals Enterprise',     'EQUITY', 'NSE', 1, 0.05),
('FORTIS',     'Fortis Healthcare Ltd',           'EQUITY', 'NSE', 1, 0.05),
('MAXHEALTH',  'Max Healthcare Institute',         'EQUITY', 'NSE', 1, 0.05),
('NH',         'Narayana Hrudayalaya',             'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: CEMENT ──────────────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('ULTRACEMCO',    'UltraTech Cement Ltd',          'EQUITY', 'NSE', 1, 0.05),
('SHREECEM',      'Shree Cement Ltd',              'EQUITY', 'NSE', 1, 0.05),
('ACC',           'ACC Ltd',                       'EQUITY', 'NSE', 1, 0.05),
('AMBUJACEMENT',  'Ambuja Cements Ltd',            'EQUITY', 'NSE', 1, 0.05),
('JKCEMENT',      'JK Cement Ltd',                'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: CHEMICALS & SPECIALTY ──────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('PIDILITIND',  'Pidilite Industries Ltd',         'EQUITY', 'NSE', 1, 0.05),
('SRF',         'SRF Ltd',                        'EQUITY', 'NSE', 1, 0.05),
('DEEPAKNTR',   'Deepak Nitrite Ltd',              'EQUITY', 'NSE', 1, 0.05),
('NAVINFLUOR',  'Navin Fluorine International',    'EQUITY', 'NSE', 1, 0.05),
('AAPL',        'Aarti Industries Ltd',            'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── SECTOR: RETAIL & E-COMMERCE ─────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('DMART',   'Avenue Supermarts Ltd',               'EQUITY', 'NSE', 1, 0.05),
('TRENT',   'Trent Ltd',                           'EQUITY', 'NSE', 1, 0.05),
('NYKAA',   'FSN E-Commerce Ventures',             'EQUITY', 'NSE', 1, 0.05),
('ZOMATO',  'Zomato Ltd',                          'EQUITY', 'NSE', 1, 0.05),
('PAYTM',   'One 97 Communications',               'EQUITY', 'NSE', 1, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── NSE INDICES for F&O ─────────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('NIFTY50',     'Nifty 50 Index',                  'INDEX', 'NSE', 50, 0.05),
('BANKNIFTY',   'Bank Nifty Index',                'INDEX', 'NSE', 15, 0.05),
('FINNIFTY',    'Nifty Financial Services',         'INDEX', 'NSE', 40, 0.05),
('MIDCPNIFTY',  'Nifty Midcap Select',             'INDEX', 'NSE', 75, 0.05),
('NIFTYNXT50',  'Nifty Next 50',                   'INDEX', 'NSE', 25, 0.05)
ON CONFLICT (symbol) DO NOTHING;

-- ─── MCX COMMODITIES ─────────────────────────────────────────────────────────
INSERT INTO instruments (symbol, name, segment, exchange, lot_size, tick_size) VALUES
('GOLD',         'Gold (1 KG)',               'COMMODITY', 'MCX',    1, 1.00),
('GOLDM',        'Gold Mini (100g)',           'COMMODITY', 'MCX',    1, 1.00),
('GOLDPETAL',    'Gold Petal (1g)',            'COMMODITY', 'MCX',    1, 1.00),
('SILVER',       'Silver (30 KG)',             'COMMODITY', 'MCX',   30, 1.00),
('SILVERM',      'Silver Mini (5 KG)',          'COMMODITY', 'MCX',    5, 1.00),
('SILVERMIC',    'Silver Micro (1 KG)',         'COMMODITY', 'MCX',    1, 1.00),
('CRUDEOIL',     'Crude Oil (100 BBL)',         'COMMODITY', 'MCX',  100, 1.00),
('CRUDEOILM',    'Crude Oil Mini (10 BBL)',     'COMMODITY', 'MCX',   10, 1.00),
('NATURALGAS',   'Natural Gas (1250 MMBTU)',    'COMMODITY', 'MCX', 1250, 0.10),
('NATURALGASM',  'Natural Gas Mini (250 MMBTU)','COMMODITY', 'MCX',  250, 0.10),
('COPPER',       'Copper (2500 KG)',            'COMMODITY', 'MCX', 2500, 0.05),
('COPPERM',      'Copper Mini (250 KG)',         'COMMODITY', 'MCX',  250, 0.05),
('LEAD',         'Lead (5000 KG)',              'COMMODITY', 'MCX', 5000, 0.05),
('ZINC',         'Zinc (5000 KG)',              'COMMODITY', 'MCX', 5000, 0.05),
('NICKEL',       'Nickel (1500 KG)',            'COMMODITY', 'MCX', 1500, 0.10),
('ALUMINIUM',    'Aluminium (5000 KG)',          'COMMODITY', 'MCX', 5000, 0.05),
('MENTHAOIL',    'Mentha Oil (360 KG)',          'COMMODITY', 'MCX',  360, 0.10),
('COTTON',       'Cotton (25 BALES)',            'COMMODITY', 'MCX',   25, 0.50),
('CASTORSEED',   'Castor Seed (10 MT)',          'COMMODITY', 'MCX', 10000, 1.00)
ON CONFLICT (symbol) DO NOTHING;

-- ─── Add sector metadata columns ─────────────────────────────────────────────
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS sector               VARCHAR(100);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS sub_sector           VARCHAR(100);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS market_cap_category  VARCHAR(20) DEFAULT 'LARGE';

-- ─── Sector tagging ──────────────────────────────────────────────────────────
UPDATE instruments SET sector = 'BANKING',    sub_sector = 'PRIVATE_BANK'
  WHERE symbol IN ('HDFCBANK','ICICIBANK','KOTAKBANK','AXISBANK','INDUSINDBK','FEDERALBNK','BANDHANBNK','IDFCFIRSTB');

UPDATE instruments SET sector = 'BANKING',    sub_sector = 'PSU_BANK'
  WHERE symbol IN ('SBIN','PNB','BANKBARODA','CANBK','UNIONBANK');

UPDATE instruments SET sector = 'NBFC'
  WHERE symbol IN ('BAJFINANCE','BAJAJFINSV','MUTHOOTFIN','CHOLAFIN');

UPDATE instruments SET sector = 'INSURANCE'
  WHERE symbol IN ('HDFCLIFE','SBILIFE','ICICIGI');

UPDATE instruments SET sector = 'IT'
  WHERE symbol IN ('TCS','INFY','WIPRO','HCLTECH','TECHM','LTI','MPHASIS','PERSISTENT','COFORGE','OFSS');

UPDATE instruments SET sector = 'OIL_GAS'
  WHERE symbol IN ('RELIANCE','ONGC','IOC','BPCL','HINDPETRO','GAIL','PETRONET','OIL','MGL','IGL');

UPDATE instruments SET sector = 'AUTO'
  WHERE symbol IN ('MARUTI','TATAMOTORS','M&M','BAJAJ-AUTO','HEROMOTOCO','EICHERMOT','TVSMOTOR','ASHOKLEY','BOSCHLTD','MOTHERSON');

UPDATE instruments SET sector = 'PHARMA'
  WHERE symbol IN ('SUNPHARMA','DRREDDY','CIPLA','DIVISLAB','BIOCON','LUPIN','AUROPHARMA','TORNTPHARM','IPCALAB','ALKEM');

UPDATE instruments SET sector = 'FMCG'
  WHERE symbol IN ('HINDUNILVR','ITC','NESTLEIND','BRITANNIA','DABUR','MARICO','COLPAL','EMAMILTD','GODREJCP','VBL');

UPDATE instruments SET sector = 'METALS'
  WHERE symbol IN ('TATASTEEL','JSWSTEEL','HINDALCO','VEDL','COALINDIA','NMDC','SAIL','NATIONALUM','HINDCOPPER','MOIL');

UPDATE instruments SET sector = 'INFRA'
  WHERE symbol IN ('LT','ADANIPORTS','POWERGRID','NTPC','ADANIENT','ADANIGREEN','SIEMENS','ABB','BEL','HAL');

UPDATE instruments SET sector = 'TELECOM'
  WHERE symbol IN ('BHARTIARTL','IDEA','TATACOMM');

UPDATE instruments SET sector = 'MEDIA'
  WHERE symbol IN ('ZEEL','SUNTV');

UPDATE instruments SET sector = 'REALTY'
  WHERE symbol IN ('DLF','GODREJPROP','PRESTIGE','OBEROIRLTY','BRIGADE');

UPDATE instruments SET sector = 'HEALTHCARE'
  WHERE symbol IN ('APOLLOHOSP','FORTIS','MAXHEALTH','NH');

UPDATE instruments SET sector = 'CEMENT'
  WHERE symbol IN ('ULTRACEMCO','SHREECEM','ACC','AMBUJACEMENT','JKCEMENT');

UPDATE instruments SET sector = 'CHEMICALS'
  WHERE symbol IN ('PIDILITIND','SRF','DEEPAKNTR','NAVINFLUOR','AAPL');

UPDATE instruments SET sector = 'RETAIL'
  WHERE symbol IN ('DMART','TRENT','NYKAA','ZOMATO','PAYTM');

UPDATE instruments SET sector = 'COMMODITY'
  WHERE exchange = 'MCX';

UPDATE instruments SET sector = 'INDEX'
  WHERE segment = 'INDEX';
