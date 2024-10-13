module com.bingbaihanji.objviewer {

    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.controls;

    opens com.bingbaihanji.objviewer to javafx.fxml;
    exports com.bingbaihanji.objviewer;
}