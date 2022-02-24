package com.nilcaream.cptidy;

import com.github.underscore.U;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Configuration {

    private Set<String> ignored = new HashSet<>();

    private Map<String, String> explicitDates = new HashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void load(Path path) throws IOException {
        Map map = U.fromJson(Files.readString(path));
        ignored.addAll(((List<String>) map.getOrDefault("ignored", Collections.emptyList())));
        explicitDates.putAll(((Map<String, String>) map.getOrDefault("explicitDates", Collections.emptyMap())));
    }

    public Set<String> getIgnored() {
        return ignored;
    }

    public void setIgnored(Set<String> ignored) {
        this.ignored = ignored;
    }

    public Map<String, String> getExplicitDates() {
        return explicitDates;
    }

    public void setExplicitDates(Map<String, String> explicitDates) {
        this.explicitDates = explicitDates;
    }
}
