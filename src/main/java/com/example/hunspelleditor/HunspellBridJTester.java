package com.example.hunspelleditor;


import com.atlascopco.hunspell.Hunspell;
import org.apache.log4j.Logger;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

// https://github.com/thomas-joiner/HunspellBridJ
public class HunspellBridJTester {

    private Hunspell stemmer;

    private static org.apache.log4j.Logger logger = Logger.getLogger(HunspellBridJTester.class);

    public static final String USERDICT = "_custom.dic";

    private int customWordCounter = 0;

    public HunspellBridJTester(String dictPath, boolean reload) {

        try {
            //log(dictPath);
//            if (reload)
//                Hunspell.getInstance().destroyDictionary(dictPath);

            close();
            stemmer = new Hunspell(dictPath + ".dic", dictPath + ".aff");
//            stemmer.addDic(dictPath + "_sajat.dic"); // ez nem sajatszotar!

            customWordCounter = loadUserDictionary(dictPath + USERDICT);

//            stemmer.addWithAffix("nautica", "alma");
//            stemmer = Hunspell.getInstance().getDictionary(dictPath);
            //log("OK");
        } catch (UnsatisfiedLinkError | UnsupportedOperationException e) {
            logger.error("hunspell loading error (" + e.getMessage() + ")");
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


    public boolean addCustomWord(String word, String example){

        try{
            stemmer.addWithAffix(word, example);
        }catch (Exception e){
            logger.info("could not add new word: " + word + " (error: " + e.getMessage() + ")");
            return false;
        }
        return true;
    }

    public int loadUserDictionary(String path){

        int counter = 0;
        File file = new File(path);
        if (!Files.exists(Paths.get(path))) {
            return counter;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("[/\\t]");
//                try {
                    if (parts.length == 2) {
                        if (addCustomWord(parts[0], parts[1]))
                            counter++;
                    }
//                        stemmer.addWithAffix(parts[0], parts[1]);
//                }catch (Exception e){
//                    logger.info("could not add new word: " + parts[0] + " (error: " + e.getMessage() + ")");
//                }
            }
            return counter;
        } catch (FileNotFoundException e) {
            logger.error("fle not found: " + path + " (" + e.getMessage() + ")");
        } catch (IOException e) {
            logger.error("io error: " + path + " (" + e.getMessage() + ")");
        }
        return counter;
    }

    public int getCustomWordCounter() {
        return customWordCounter;
    }
}
