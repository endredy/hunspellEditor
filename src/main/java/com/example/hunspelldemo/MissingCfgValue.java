package com.example.hunspelldemo;

import java.io.IOException;

public class MissingCfgValue extends IOException {

    public MissingCfgValue(String s){
        super(s);
    }
}
