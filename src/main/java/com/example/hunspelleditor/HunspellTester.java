package com.example.hunspelleditor;

import dk.dren.hunspell.Hunspell;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.List;

public class HunspellTester{

    private Hunspell.Dictionary stemmer;

    private static org.apache.log4j.Logger logger = Logger.getLogger(HunspellTester.class);

    public HunspellTester(String dictPath, boolean reload) {

        try {
            //log(dictPath);
            if (reload)
                Hunspell.getInstance().destroyDictionary(dictPath);

            stemmer = Hunspell.getInstance().getDictionary(dictPath);
            //log("OK");
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            logger.error("io hiba: " + e.getMessage());
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            logger.error("ecoding hiba: " + e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            // TODO Auto-generated catch block
            logger.error("link hiba: " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            // TODO Auto-generated catch block
            logger.error("nem tamogatott muvelet: " + e.getMessage());
        }
    }

    public List<String> getStemList(String word) {

        List<String> helper = stemmer.stem(word);
        return helper;
    }

}
