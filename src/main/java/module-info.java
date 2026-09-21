module org.maverick.documentergui {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.net.http;

    opens org.maverick to javafx.fxml;
    exports org.maverick;
    exports org.maverick.factories;
}