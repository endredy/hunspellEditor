package com.example.hunspelleditor;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class DictionaryManager {

    private String dictPath = "hunspell/";
    private String langCode = "hu_HU";

    private Map<String, DictionaryItem> words  = new HashMap<>();
    private boolean dictChanged = false;

    private int dictWordCounter = 0;
    private int customDictWordCounter = 0;

    private static org.apache.log4j.Logger logger = Logger.getLogger(DictionaryManager.class);

    public DictionaryManager(String dictPath, String langCode) {
        this.dictPath = dictPath;
        this.langCode = langCode;
    }

    public int loadDictionary(String path, boolean withPOS){

        words.clear();

        HunspellBridJTester h = new HunspellBridJTester(dictPath + langCode, false);
        customDictWordCounter = h.getCustomWordCounter();

        Map<Integer, String> posSet = new HashMap<>();
        boolean first = true;
        int counter = 0;
        File file = new File(path);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                if (first){
                    first = false;
                    continue; //skip the 1st line
                }
                counter++;
                DictionaryItem d = new DictionaryItem(line);

                // get pos (each type only once :))
                if (withPOS) {
                    String pos = posSet.get(d.getCode());
                    if (pos == null) {
                        // it hasn't analysed yet
                        d.setPos(h.getPOS(d.getWord()));
                        posSet.put(d.getCode(), d.getPos());
                    } else
                        d.setPos(pos);
                }

                DictionaryItem e = words.get(d.getKey());
                if (e != null){
                    e.increaseCounter();
                }else {
                    words.put(d.getKey(), d);
                }
            }

            h.close();
        } catch (IOException e) {
            logger.error("io error: " + e.getMessage());
        }
        dictWordCounter = counter;
        return counter;
    }

    public List<DictionaryItem> findSimilar(String word, String hunspellPOS, int last, boolean properNoun) {

        int from = word.length() - (last >= word.length() ? word.length() : last);
        if (from < 0) from = 0;
        String lastCh = word.substring(from);

        String finalHunspellPOS = hunspellPOS;
        Map<String, DictionaryItem> subset = words.entrySet()//.keySet()
                .stream()
                .filter(s -> s.getValue().getWord().endsWith(lastCh) &&
                             (finalHunspellPOS == null || (s.getValue().getPos() != null && s.getValue().getPos().startsWith(finalHunspellPOS))) &&
                             (properNoun || !s.getValue().getWord().isEmpty() && Character.isLowerCase(s.getValue().getWord().charAt(0))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));//Set());

        List<DictionaryItem> list = new ArrayList<>(subset.values());
        //        list.sort(Comparator.comparing(DictionaryItem::getCounter).reversed()); // ranking by frequency
        list.sort(Comparator.comparing(DictionaryItem::getWord)); // ranking by word

        return list;
    }

    public void addNewWord(String newWord, DictionaryItem similar){
        String line = similar.getOriginal().replace(similar.getWord(), newWord);

        dictBackup(true);

        addLineToFile(line, dictPath + langCode + ".dic");

        dictChanged = true;
    }

    public void addNewWordToCustomDict(String newWord, DictionaryItem similar){

        String line = newWord + "/" + similar.getWord();

        // add to custom dictionary:
        // restore dictionary
        dictBackup(true);

        // add to custom dictionary
        if (addLineToFile(line, dictPath + langCode + HunspellBridJTester.USERDICT)){
            customDictWordCounter++;
        }
    }

    /** ha van backup: visszaallitja, ha nincs letrehozza
     *
     * @param restore restore from previous state (true) or backup this state (false)
     */
    public void dictBackup(boolean restore){

        Path backup = Paths.get(dictPath + langCode + ".dic.bak");
        Path prev = Paths.get(dictPath + langCode + ".dic.prev");
        Path live = Paths.get(dictPath + langCode + ".dic");

        try {

            if (Files.exists(backup)) {
                if (restore){
                    if (Files.exists(prev)) {
                        // restores state from the previous state of the dictionary
                        Files.copy(prev, live, StandardCopyOption.REPLACE_EXISTING);
                    }else{
                        Files.copy(live, prev, StandardCopyOption.REPLACE_EXISTING);
                    }
                }else{
                    Files.copy(live, prev, StandardCopyOption.REPLACE_EXISTING);
                }
            }else{
                // create a backup from the original dic
                Files.copy(live, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.error("error at file copy: " + e.getMessage());
        }
    }

    private boolean addLineToFile(String line, String filePath){
        try {
            Writer output = new BufferedWriter(new FileWriter(filePath, true));
            output.append(line).append("\n");
            output.close();
            return true;
        } catch (IOException e) {
            logger.error("we couldn't add a new word: " + e.getMessage());
        }
        return false;
    }

    public int convertDictionary(){

        String customDictPath = dictPath + langCode + HunspellBridJTester.USERDICT;
        File file = new File(customDictPath);
        if (!Files.exists(Paths.get(customDictPath))) {
            return 1; // empty dictionary
        }
        int counter = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("[/\\t]");
                if (parts.length == 2) {

                    String newWord = parts[0];
                    String existingWord = parts[1];

                    DictionaryItem similar = words.values()
                            .stream()
                            .filter(s -> s.getWord().equals(existingWord)).findFirst().orElse(null);

                    if (similar != null) {
                        String dictLine = similar.getOriginal().replace(similar.getWord(), newWord);
                        if (addLineToFile(dictLine, dictPath + langCode + ".dic")){
                            counter++;
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("fle not found: " + customDictPath + " (" + e.getMessage() + ")");
        } catch (IOException e) {
            logger.error("io error: " + customDictPath + " (" + e.getMessage() + ")");
        }

        dictWordCounter += counter;
        return 0;
    }

    public void increaseDictWordCounter(){
        dictWordCounter++;
    }
    public int getDictWordCounter() {
        return dictWordCounter;
    }

    public int getCustomDictWordCounter() {
        return customDictWordCounter;
    }

    public boolean isDictChanged() {
        return dictChanged;
    }
}
