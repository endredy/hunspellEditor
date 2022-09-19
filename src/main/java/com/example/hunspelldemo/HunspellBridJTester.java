package com.example.hunspelldemo;


import com.atlascopco.hunspell.Hunspell;
import org.apache.log4j.Logger;


import java.io.*;
import java.util.List;

// https://github.com/thomas-joiner/HunspellBridJ
public class HunspellBridJTester {

    private Hunspell stemmer;

    private static org.apache.log4j.Logger logger = Logger.getLogger(HunspellBridJTester.class);

    public static final String USERDICT = "_sajat.dic";

    public HunspellBridJTester(String dictPath, boolean reload) {

        try {
            //log(dictPath);
//            if (reload)
//                Hunspell.getInstance().destroyDictionary(dictPath);

            close();
            stemmer = new Hunspell(dictPath + ".dic", dictPath + ".aff");
//            stemmer.addDic(dictPath + "_sajat.dic"); // ez nem sajatszotar!

            loadUserDictionary(dictPath + USERDICT);

//            stemmer.addWithAffix("nautica", "alma");
//            stemmer = Hunspell.getInstance().getDictionary(dictPath);
            //log("OK");
        } catch (UnsatisfiedLinkError | UnsupportedOperationException e) {
            logger.error("hunspell betoltesi hiba (" + e.getMessage() + ")");
        }
    }

    public void close(){
        if (stemmer != null)
            stemmer.close();
    }

    public List<String> getStemList(String word) {

        List<String> helper = stemmer.stem(word);
        return helper;
    }

    public List<String> getAnalysationList(String word) {

        List<String> helper = stemmer.analyze(word);
        return helper;
    }

    public List<String> generate(String word, String example){
        return stemmer.generate(word, example);
    }

    public String getPOS(String word){

        for(String x : stemmer.analyze(word)) {
            int from = x.indexOf("po:");
            if (from != -1) {
                from += 3;
                int to = x.indexOf(" ", from);
                return to == -1 ? x.substring(from) : x.substring(from, to);
            }
        }
        return null;
    }

    public boolean loadUserDictionary(String path){

        File file = new File(path);
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("[/\\t]");
                try {
                    if (parts.length == 2)
                        stemmer.addWithAffix(parts[0], parts[1]);
                }catch (Exception e){
                    logger.info("ezt nem tudta felvenni: " + parts[0] + " (error: " + e.getMessage() + ")");
                }
            }
            return true;
        } catch (FileNotFoundException e) {
            logger.error("nem talaltuk a fajlt: " + path + " (" + e.getMessage() + ")");
        } catch (IOException e) {
            logger.error("io hiba: " + path + " (" + e.getMessage() + ")");
        }
        return false;
    }
}
