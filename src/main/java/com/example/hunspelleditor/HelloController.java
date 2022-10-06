package com.example.hunspelleditor;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;


public class HelloController implements Initializable {

    private static org.apache.log4j.Logger logger = Logger.getLogger(HelloController.class);
    public Label similarLabel;
    public Label dictNameLabel;
    public Label dictWCounterLabel;
    public Label customDictNameLabel;
    public Label customDictWCounterLabel;
    public CheckBox similarProperNounCheckbox;
    public TextField pronunciationText;

    @FXML
    private TextField inputText;

    @FXML
    private ChoiceBox<String> partOfSpeech;

    @FXML
    private ListView<DictionaryItem> similarWordList;

    @FXML
    private Spinner similarCharLimitSpinner;

    @FXML
    private TableView<String[]> test2List;

    @FXML
    private ChoiceBox<TestSuffixSet> testSuffixes;

    @FXML
    private TextField freeText;

    @FXML
    private Text freeTextResult;

    @FXML
    private Text freeTextAnalysation;

    @FXML
    private RadioButton addToDict;

    @FXML
    private RadioButton addToCustom;
    private List<TestSuffixSet> suffix = new ArrayList<>();

    private Map<String, String> captionsOfHunspellPOS = new HashMap<>(); // caption -> hunpos (pl "ige" -> "vrb"

    private HunspellBridJTester hunspellFreeTextTester = null;
    private boolean dictChanged = false;

    private String dictPath = "hunspell/";
    private String langCode = "hu_HU";

    private DictionaryManager dictionaryManager;
    private ResourceBundle bundle;

    @FXML
    public void initialize(URL location, ResourceBundle resources)  {

        bundle = resources;

        boolean iniOK = loadIni("test.cfg");

        dictionaryManager = new DictionaryManager(dictPath, langCode);
        dictionaryManager.loadDictionary(dictPath + langCode + ".dic", true);
        setDictionaryCounter(false, dictionaryManager.getDictWordCounter());
        setDictionaryCounter(true, dictionaryManager.getCustomDictWordCounter());
        dictNameLabel.setText( langCode + ".dic: ");
        customDictNameLabel.setText( langCode + HunspellBridJTester.USERDICT + ": ");

        List<String> posList = suffix.stream().map(TestSuffixSet::getPos).distinct().collect(Collectors.toList());
        posList.add(getCaption("any"));

        ObservableList<String> oPosList = FXCollections.observableArrayList();
        partOfSpeech.setItems(oPosList);
        oPosList.addAll(posList);
        partOfSpeech.setValue(posList.get(0));

        ObservableList<TestSuffixSet> oSuffisList = FXCollections.observableArrayList();
        testSuffixes.setItems(oSuffisList);
        List<TestSuffixSet> captions = getCurrentSuffixSets();
        oSuffisList.addAll(captions);
        if (!captions.isEmpty())
            testSuffixes.setValue(captions.get(0));//suffix.keySet().stream().findFirst().get());//"főnév");

        ObservableList<DictionaryItem> outList = FXCollections.observableArrayList();
        similarWordList.setItems(outList);

        TableColumn<String[],String> columnOne = new TableColumn<>(getCaption("wordform"));
        TableColumn<String[],String> columnTwo = new TableColumn<>(getCaption("stem"));

        columnOne.setPrefWidth(200);
        columnTwo.setPrefWidth(200);
        test2List.getColumns().addAll(columnOne, columnTwo);
        columnOne.setCellValueFactory((p)->{
            String[] x = p.getValue();
            return new SimpleStringProperty(x != null && x.length>0 ? x[0] : "<no name>");
        });
        columnTwo.setCellValueFactory((p)->{
            String[] x = p.getValue();
            return new SimpleStringProperty(x != null && x.length>1 ? x[1] : "<no value>");
        });

        final ObservableList<String[]> data = FXCollections.observableArrayList(
//                new String[]{"a1", "b1"},
//                new String[]{"a2", "b2"}
        );
        test2List.setItems(data);

        partOfSpeech.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {

            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                // Your action here
                System.out.println("Selected item: " + newValue);
                testSuffixes.getItems().clear();
                for(TestSuffixSet t : getCurrentSuffixSets()){
                    if (t.getPos().equals(newValue)){
                        testSuffixes.getItems().add(t);
                    }
                }
                if (testSuffixes.getItems().isEmpty())
                    return;
                // it has content:
                testSuffixes.setValue(testSuffixes.getItems().get(0));
                testWordForms(false);
            }
        });

        similarWordList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<DictionaryItem>() {

            @Override
            public void changed(ObservableValue<? extends DictionaryItem> observable, DictionaryItem oldValue, DictionaryItem newValue) {
                // Your action here
                if (newValue == null)
                    return;
                System.out.println("Selected item: " + newValue.getWord());
                checkNewWord(inputText.getText(), newValue);
            }
        });

        testSuffixes.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<TestSuffixSet>() {

            @Override
            public void changed(ObservableValue<? extends TestSuffixSet> observable, TestSuffixSet oldValue, TestSuffixSet newValue) {
                testWordForms(false);
            }
        });


        similarCharLimitSpinner.setTooltip(new Tooltip(getCaption("suffixSearchLimit")));
        similarCharLimitSpinner.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (!"".equals(newValue)) {
                findSimilarEx(Integer.valueOf(newValue));
            }
        });
        similarProperNounCheckbox.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            findSimilarEx(-1);
        });

        inputText.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    testWordForms(false);
                }
            }
        });
    }

    private boolean loadIni(String configFile) {
        InputStream input = null;
        boolean ret = false;

        try{
            input = new FileInputStream(configFile);
            InputStreamReader isr = new InputStreamReader(input, Charset.forName("ISO-8859-2"));
            Map<String, Properties> ini = ConfigReader.parseINI(isr);

            for(Map.Entry<String, Properties> e : ini.entrySet()) {
                if ("ui".equals(e.getKey()) || "hunspell".equals(e.getKey()))
                    continue;
                String[] name = e.getKey().split("#");
                String pos = name[0];
                String caption = name.length > 1 ? name[1] : "";
                suffix.add(new TestSuffixSet(pos, caption, e.getValue().keySet().stream().toArray(String[] ::new)));
            }

            Properties m = ini.get("hunspell");
            if (m != null) {
                for(Map.Entry<Object, Object> e : m.entrySet()) {
                    if ("path".equals(e.getKey())) {
                        dictPath = (String) m.get("path");
                    } else if ("langCode".equals(e.getKey())) {
                        langCode = (String) m.get("langCode");
                    } else {
                        captionsOfHunspellPOS.put(e.getValue().toString(), e.getKey().toString());
                    }
                }
            }

            ret = true;
        }catch (MissingCfgValue ex){
            logger.info(ex.getMessage());
            ret = false;
        } catch (IOException ex) {
            logger.info(ex.getMessage());
            ret = false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    logger.error("io error: " + e.getMessage());
                    ret = false;
                }
            }
        }

        return ret;
    }

    private String getCaption(String id){
        return bundle.getString(id);
    }

    private void findSimilar(String word, String pos, int last, boolean properNoun){

        String hunspellPOS = captionsOfHunspellPOS.get(pos);

        int from = word.length() - (last >= word.length() ? word.length() : last);
        if (from < 0) from = 0;
        String lastCh = word.substring(from);

        List<DictionaryItem> list = dictionaryManager.findSimilar(word, hunspellPOS, last, properNoun);
        similarLabel.setText(getCaption("similarTo") + "  (" + (from > 0 ? "..." : "") + lastCh + ", " + list.size() + ")");

        similarWordList.getItems().clear();
        similarWordList.getItems().addAll(list);
    }

    @FXML
    protected void onAddWordButtonClick() {

        DictionaryItem similar = similarWordList.getSelectionModel().getSelectedItem();
        if (similar == null){
            Alert alert = new Alert(Alert.AlertType.INFORMATION, getCaption("noSimilarWord"), ButtonType.CLOSE);
            alert.showAndWait();
            return;
        }

        if (addToDict.isSelected()){
            dictionaryManager.increaseDictWordCounter();
            setDictionaryCounter(false, dictionaryManager.getDictWordCounter());
            dictionaryManager.dictBackup(false); // save this state to prev dic
            return; // new word is already in the dictionary
        }

        // check adding word
        if (hunspellFreeTextTester == null) {
            hunspellFreeTextTester = new HunspellBridJTester(dictPath + langCode, false);
        }
        if (!hunspellFreeTextTester.addCustomWord(inputText.getText(), similar.getWord())){
            // could not add word (by Hunspell add_with_affix())
            Alert alert = new Alert(Alert.AlertType.INFORMATION, getCaption("cannotAddWord"), ButtonType.CLOSE);
            alert.showAndWait();
            return;
        }

        dictionaryManager.addNewWordToCustomDict(inputText.getText(), similar);
        setDictionaryCounter(true, hunspellFreeTextTester.getCustomWordCounter());
    }

    private void setDictionaryCounter(boolean custom, int value){
        if (custom){
            customDictWCounterLabel.setText( String.valueOf(value));
        }else{
            dictWCounterLabel.setText( String.valueOf(value));
        }
    }

    private void checkNewWord(String newWord, DictionaryItem similar){

        dictionaryManager.addNewWord(newWord, similar);

        testWordForms(true);
        if (!freeText.getText().isEmpty()){
            onFreeTextKeyUp(null);
        }
    }

    private List<String> getCurrentSuffixSetCaptions(){
        String pos = partOfSpeech.getValue();
        List<String> posSuffixSets = suffix.stream().filter(a -> a.getPos().equals(pos)).map(TestSuffixSet::getName).collect(Collectors.toList());
        return posSuffixSets;
    }

    private List<TestSuffixSet> getCurrentSuffixSets(){
        String pos = partOfSpeech.getValue();
        List<TestSuffixSet> posSuffixSets = suffix.stream().filter(a -> a.getPos().equals(pos)).collect(Collectors.toList());
        return posSuffixSets;
    }
    private String[] getCurrentSuffixSetValues(){
        TestSuffixSet name = testSuffixes.getValue();
//        String pos = partOfSpeech.getValue();
        if (name == null) {
            logger.info("sorry to say the generating set is empty");
            return new String[]{};
        }
        return name.getSuffixes();
    }

    private void testWordForms(boolean reload){

//        HunspellTester h = new HunspellTester(dictPath, reload);
        HunspellBridJTester h = new HunspellBridJTester(dictPath + langCode, reload);

        String[] s = getCurrentSuffixSetValues();

        String word = inputText.getText(); // testStem.getText();
        test2List.getItems().clear();

        for(String example : s){

            // we are generating :)
            List<String> forms = h.generate(word, example);
            for(String w : forms){
                List<String> r = h.getStemList(w);
                test2List.getItems().add(new String[]{w, String.join(",", r) + (!r.isEmpty() ? " \u2705" : " \u274C")});
            }
            if (forms.isEmpty()){
                test2List.getItems().add(new String[]{word +" (" + example + ")", "\u274C (" + getCaption("couldntGenerate")+ ")"});
            }
        }
    }
    public void onSearchButtonClick(ActionEvent actionEvent) {

        int x = (int)similarCharLimitSpinner.getValue();
        similarCharLimitSpinner.setValueFactory( new SpinnerValueFactory.IntegerSpinnerValueFactory(1, inputText.getText().length(), x));

        findSimilarEx(-1);
    }

    private void findSimilarEx(int spinnerLimit){
        findSimilar(pronunciationText.getText().isEmpty() ? inputText.getText() : pronunciationText.getText(),
                partOfSpeech.getValue(), (int)similarCharLimitSpinner.getValue(), similarProperNounCheckbox.isSelected());
    }

    public void onStemButtonClick(ActionEvent actionEvent) {

        testWordForms(false);
    }

    public void onFreeTextKeyUp(KeyEvent keyEvent) {

        if (hunspellFreeTextTester == null || dictChanged) {
            hunspellFreeTextTester = new HunspellBridJTester(dictPath + langCode, false);
            dictChanged = false;
        }
        List<String> r = hunspellFreeTextTester.getStemList(freeText.getText());
        freeTextResult.setText(r.isEmpty() ? "" : " " + String.join("\n ", r));

        r = hunspellFreeTextTester.getAnalysationList(freeText.getText());
        freeTextAnalysation.setText(r.isEmpty() ? "" : String.join("\n\n", r));
    }

    public void onConvertDictionaryButtonClick(ActionEvent actionEvent) {

        int r = dictionaryManager.convertDictionary();

        if (r == 1){
            Alert alert = new Alert(Alert.AlertType.INFORMATION, getCaption("emptyCustomDictionary"), ButtonType.CLOSE);
            alert.showAndWait();
            return;
        }

        setDictionaryCounter(false, dictionaryManager.getDictWordCounter());
    }
}
