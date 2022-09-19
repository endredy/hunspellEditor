package com.example.hunspelldemo;

public class TestSuffixSet {
    private String pos;
    private String name;
    private String[] suffixes;

    public TestSuffixSet(String pos, String name, String[] suffixes) {
        this.pos = pos;
        this.name = name;
        this.suffixes = suffixes;
    }

    public String getPos() {
        return pos;
    }

    public String getName() {
        return name;
    }

    public String[] getSuffixes() {
        return suffixes;
    }

    @Override
    public String toString() {
        return name + " ("+pos+")";
    }
}
