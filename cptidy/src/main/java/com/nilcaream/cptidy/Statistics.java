package com.nilcaream.cptidy;

import java.util.Map;
import java.util.TreeMap;

public class Statistics {

    private final String id;
    private Map<String, Record> data = new TreeMap<>();

    public Statistics(String id) {
        this.id = id;
    }

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

    public boolean hasData() {
        return data.size() > 0;
    }

    public String getId() {
        return id;
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

        @Override
        public String toString() {
            return String.format("%d elements, %.3f MB", count, bytes / (1024.0 * 1024.0));
        }
    }
}
