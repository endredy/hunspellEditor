package com.example.hunspelleditor;

import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DictionaryManager {

    private String dictPath = "hunspell/";
    private String langCode = "hu_HU";

    private Map<String, DictionaryItem> words  = new HashMap<>();
    private boolean dictChanged = false;

    private int dictWordCounter = 0;
    private int customDictWordCounter = 0;

    private int amSize = -1;
    private int amOffset = -1;

    class WordInfo{
        List<String> aff = new ArrayList<>();
        List<DictionaryItem> dic = new ArrayList<>();
    }

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
                        pos = h.getPOS(d.getWord());
                        if (pos != null) {
                            d.setPos(pos);
                            posSet.put(d.getCode(), d.getPos());
                        }
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
//        String reverse = new StringBuilder(lastCh).reverse().toString();

        String finalHunspellPOS = hunspellPOS;
        Map<String, DictionaryItem> subset = words.entrySet()//.keySet()
                .stream()
                .filter(s -> s.getKey().endsWith(lastCh) && //s.getKey().startsWith(reverse) && //s.getValue().getWord().endsWith(lastCh) &&
                             (finalHunspellPOS == null || (s.getValue().getPos() != null && s.getValue().getPos().startsWith(finalHunspellPOS))) &&
                             (properNoun || !s.getValue().getWord().isEmpty() && Character.isLowerCase(s.getValue().getWord().charAt(0))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));//Set());

        List<DictionaryItem> list = new ArrayList<>(subset.values());
        //        list.sort(Comparator.comparing(DictionaryItem::getCounter).reversed()); // ranking by frequency
        list.sort(Comparator.comparing(DictionaryItem::getWord)); // ranking by word

        return list;
    }

    public void addNewWord(String newWord, DictionaryItem similar){

        WordInfo info = getAffInfo(similar);

        dictBackup(true);
        if (info.dic.size() == 1) {
            // simple case: 1 line is enough in dictionary
            String line = similar.getOriginal().replace(similar.getWord(), newWord);

            addLineToFile(line, dictPath + langCode + ".dic");
        }else{
            // complicated case
            List<String> newDictLines = new ArrayList<>(), newAffLines = new ArrayList<>();

            int c = 0;
            for(DictionaryItem d : info.dic){
                String line = d.getOriginal().replace(similar.getWord(), newWord);
                int n = d.getAffNumber();
                line = line.replace(String.valueOf(n), String.valueOf(++c + amSize));
                newDictLines.add(line);
            }
            for(String aff : info.aff){
                String line = aff.replace(similar.getWord(), newWord);
                newAffLines.add(line);
            }

            addLinesToAff(newAffLines, dictPath + langCode + ".aff");
            addLinesToFile(newDictLines, dictPath + langCode + ".dic");
        }

        dictChanged = true;
    }

    public void addNewWordToCustomDict(String newWord, DictionaryItem similar){

        String line = newWord + "/" + similar.getWord();

        // add to custom dictionary:
        // restore dictionary
        dictBackupEx(true, true);

        // add to custom dictionary
        if (addLineToFile(line, dictPath + langCode + HunspellBridJTester.USERDICT)){
            customDictWordCounter++;
        }
    }

    /** ha van backup: visszaallitja, ha nincs letrehozza
     *
     * @param restore restore from previous state (true) or backup this state (false)
     */
    public void dictBackup(boolean restore) {
        dictBackupEx(true, restore);
        dictBackupEx(false, restore);
    }
    public void dictBackupEx(boolean dic, boolean restore){

        String ext = dic ? "dic" : "aff";
        Path backup = Paths.get(dictPath + langCode + "." + ext + ".bak");
        Path prev = Paths.get(dictPath + langCode + "." + ext + ".prev");
        Path live = Paths.get(dictPath + langCode + "." + ext);

        try {

            if (Files.exists(backup)) {
                if (restore){
                    if (Files.exists(prev)) {
                        // restores state from the previous state of the dictionary
                        Files.copy(prev, live, StandardCopyOption.REPLACE_EXISTING);
                        if (!dic){
                            // load prev aff params
                            amSize = -1;
                            getAffLine(1);
                        }
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

    private boolean addLinesToFile(List<String> lines, String filePath){
        try {
            Writer output = new BufferedWriter(new FileWriter(filePath, true));
            for(String line : lines) {
                output.append(line).append("\n");
            }
            output.close();
            return true;
        } catch (IOException e) {
            logger.error("we couldn't add a new word: " + e.getMessage());
        }
        return false;
    }

    private boolean addLineToFile(String line, String filePath){
       return addLinesToFile(Collections.singletonList(line), filePath);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static int indexOf(byte[] arr, byte val, int from) {
        return IntStream.range(from, arr.length).filter(i -> arr[i] == val).findFirst().orElse(-1);
    }
    private boolean addLinesToAff(List<String> lines, String filePath){

        File file = new File(filePath);
        File file2 = new File(filePath + "tmp");
        if (file2.exists())
            file2.delete();
        boolean success = file.renameTo(file2);

        if (!success){
            logger.error("file could not be renamed: " + filePath);
            return false;
        }

        int counter = 0;
//        try (BufferedReader br = new BufferedReader( new InputStreamReader(new FileInputStream(file2), StandardCharsets.UTF_8)/*new FileReader(file2)*/)) {

//            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try (InputStream inputStream = new FileInputStream(file2)){
            OutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[1024], line, remainder = null;
            int bytesRead = -1, from = 0, to = 0, copied = 0;//, lineLength = 0, remainderLength = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {

                while(true) {
                    to = indexOf(buffer, (byte) 10, from);
                    if (to == -1 || from >= bytesRead){
                        if (copied == 0) {
                            // whole line doesn't contain newline (a very long line)
                            remainder = concat(remainder, buffer);
                        }else if (copied < bytesRead)
                            remainder = Arrays.copyOfRange(buffer, copied, bytesRead);
                        from = 0;
                        copied = 0;
                        break;
                    }
                    if (remainder == null || remainder.length == 0){
                        line = Arrays.copyOfRange(buffer, from, to+1);
                    }else{
                        line = concat(remainder, Arrays.copyOfRange(buffer, from, to+1));
                        remainder = null;
                    }
                    from = to + 1;
                    copied = from;

                    if (counter == amOffset) {
                        // write new AM size
                        outputStream.write(String.format("AM %d\r\n", amSize + lines.size()).getBytes()); //.getBytes(StandardCharsets.UTF_8)
                    } else if (counter == amOffset + amSize) {
                        outputStream.write(line);
                        // end of the AM section, append new lines
                        for (String l : lines) {
                            outputStream.write((l + "\r\n").getBytes(StandardCharsets.UTF_8));
                        }
                    } else {
                        outputStream.write(line);
                    }
                    counter++;
                }
            }

            amSize += lines.size(); // update counter
//            String line;
//            br.read()
/*            while ((line = br.readLine()) != null) {
                line += "\r\n";
                if (counter == amOffset){
                    // write new AM size
                    out.write( String.format("AM %d\r\n", amSize+lines.size())); //.getBytes(StandardCharsets.UTF_8)
                }else if (counter == amOffset + amSize){
                    // end of the AM section, append new lines
                    for(String l : lines){
                        out.write(l + "\r\n");
                    }
                }else{
                    out.write(line);
                }
                counter++;
            }
            out.close();
*/
            outputStream.close();
            inputStream.close();
            file2.delete();
        } catch (IOException e) {
            logger.error("io error: " + e.getMessage());
            return false;
        }
        return true;
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


    private WordInfo getAffInfo(DictionaryItem similar) {

        int affLineNumber = similar.getAffNumber();
        Map<Integer, String> affLineCache = getAffLine(affLineNumber);
        WordInfo result = new WordInfo();
//        List<String> aff = new ArrayList<>();
//        List<DictionaryItem> dic = new ArrayList<>();
        String affLine = affLineCache.get(affLineNumber);
        if (affLine != null){

            try {
                Pattern regex = Pattern.compile("al:(.*?) ", Pattern.CANON_EQ);
                Matcher regexMatcher = regex.matcher(affLine);
                while (regexMatcher.find()) {
//                    regexMatcher.group(); regexMatcher.start(); regexMatcher.end();
                    String w = regexMatcher.group(1);

                    DictionaryItem d = words.values().stream().filter(s -> w.equals(s.getWord())).findFirst().orElse(null);
                    result.dic.add(d);

                    String tmp = affLineCache.get(d.getAffNumber());
                    if (tmp != null){
                        result.aff.add(tmp);
                    }
                    logger.info("related word: " + w + ", its line from dict: " + d.getOriginal() + " from aff: " + tmp);
                }
                result.aff.add(affLine); // last line of aff
                result.dic.add(similar); // last line of dic

            } catch (PatternSyntaxException ex) {
                // Syntax error in the regular expression
            }

            // more words are affected: "AM po:noun ts:NOM al:cache-sé al:cache-sel al:cache-es al:cache- ph:kess"
        }
        return result;
    }
    /**
     * AF ...
     * AF ...
     * AM 22806                          <-- ez a sor leírja, hány sora van a AM résznek, pontosnak kell lennie
     * AM po:noun ts:NOM                 <-- ez az 1. AM sor, így hivatkoznak rá (pl: "üzbég/11        1") mivel az AF 1264 soros, ezért 1264 + 1 = 1265. sora az aff-nak
     * AM po:noun ts:NOM al:üzletágak
     * ...
     *
     * */
    public Map<Integer, String> getAffLine(int lineNumber) {

        if (lineNumber == -1)
            return null; // this wor has no aff AM line

        Map<Integer, String> lastLines = new HashMap<>();
        boolean amFound = amSize != -1, amSection = false;
        int counter = 0;
        String path = dictPath + langCode + ".aff";
        File file = new File(path);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // process the line.
                if (!amFound && !amSection){
                    if (line.startsWith("AM")) {
                        // line: "AM 22806"
                        amOffset = counter;
                        amSize = Integer.parseInt(line.substring(2).trim());
                        amSection = true;
                        counter = 0;
                    }
                }else {
                    if (!amSection){
                        if(amOffset == counter) {
                            // reached the am section
                            amSection = true;
                            counter = 0;
                        }
                    }else{
                        if (lineNumber == counter){
                            // bingo
                            lastLines.put(lineNumber, line);
                            return lastLines;
//                            logger.info("find line in aff: " + line);
//                            break;
                        }else{
                            // keep last x lines up to date
                            if (counter + 20 > lineNumber){
                                lastLines.put(counter, line);
                            }
                        }
                    }
                }
                counter++;
            }
        } catch (FileNotFoundException e) {
            logger.error("file not found: " + path + " (" + e.getMessage() + ")");
        } catch (IOException e) {
            logger.error("io error: " + path + " (" + e.getMessage() + ")");
        }
        return null;
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
