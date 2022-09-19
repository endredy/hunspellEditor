package com.example.hunspelldemo;


public class DictionaryItem {

    private String word;
    private int code;

    private String original;

    private String key;
    private String pos;
    private int counter;

    public static final int MAX_SEARCH_LENGTH = 10; // so many character will be searched (from the left of the words in dictionary) (the bigger length the bigger search map)

    public DictionaryItem(String line) {
        this.original = line;

        String[] a = line.split("[\\t/]"); //\\b
        word = a[0];
        code = 0;
        try {
            if (a.length > 1)
                code = Integer.parseInt(a[1]); // TODO: "úgysincs/1      1", "úgyannyira      15"
        }catch (NumberFormatException e){}

        if (word.length() > 0)
            key = word.substring(word.length() > MAX_SEARCH_LENGTH ? word.length() - MAX_SEARCH_LENGTH : 0) + code;
        else
            key = "<...>";
        counter = 1;
        pos = ""; // TODO
    }

    public String getKey() {
        return key;
    }

    public String getOriginal() {
        return original;
    }

    public String getWord() {
        return word;
    }

    public void increaseCounter(){
        counter++;
    }

    public int getCounter() {
        return counter;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return word + "/" + code + " ("+counter+")";
    }
}
