module com.example.hunspelldemo {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires hunspell.bridj;
    requires log4j;

    opens com.example.hunspelleditor to javafx.fxml;
    exports com.example.hunspelleditor;
}
