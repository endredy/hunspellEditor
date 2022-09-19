package com.example.hunspelldemo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigReader {

    public static String getCheckedProperty(Properties prop, String name) throws IOException {
        return getCheckedProperty(prop, name, null);
    }
    public static String getCheckedProperty(Properties prop, String name, String def) throws IOException {
        String value = prop.getProperty(name, def);
        if (value == null)
            throw new MissingCfgValue("a cfg-bol hianyzik: " + name);
        return value.trim();
    }
    public static String getProperty(Properties prop, String name, String def) throws IOException {
        String value = prop.getProperty(name, def);
        return (value == null ? def : value.trim());
    }

    public static Map<String, Properties> parseINI(/*InputStream*/InputStreamReader reader) throws IOException {
        Map<String, Properties> result = new HashMap();
        new Properties() {

            private Properties section = new Properties();

            @Override
            public Object put(Object key, Object value) {
                String header = (((String) key) + " " + value).trim();
                if (header.startsWith("[") && header.endsWith("]"))
                    return result.put(header.substring(1, header.length() - 1),
                            section = new Properties());
                else
                    return section.put(key, value);
            }

        }.load(reader);
        return result;
    }

}
