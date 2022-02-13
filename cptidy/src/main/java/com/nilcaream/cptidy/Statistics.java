package com.nilcaream.cptidy;

import java.util.Map;
import java.util.TreeMap;

public class Statistics {

    private Map<String, Record> data = new TreeMap<>();

    public void add(String key, long bytes) {
        Record record = data.get(key);
        if (record == null) {
            record = new Record();
            data.put(key, record);
        }
        record.count++;
        record.bytes += bytes;
    }

    public Map<String, Record> getData() {
        return data;
    }

    public static final class Record {
        private int count;
        private long bytes;

        public int getCount() {
            return count;
        }

        public long getBytes() {
            return bytes;
        }
    }
}
