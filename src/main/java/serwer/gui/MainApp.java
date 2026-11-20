package serwer.gui;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import serwer.model.StorageType;
import serwer.model.SystemParams;
import serwer.sim.Experiment;
import serwer.sim.RunResult;
import serwer.stats.Summary;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import serwer.sla.SlaEvaluator;
import serwer.sla.SlaLevel;
import serwer.sla.SlaSummary;
import java.util.ArrayList;

public class MainApp extends Application {

    private record Input(SystemParams params, int runs, long seed) {}
    private record Output(List<RunResult> runs, List<SlaSummary> sla) {}

    private final Map<String, TextField> fields = new HashMap<>();
    private final ComboBox<StorageType> storageBox = new ComboBox<>();
    private final Button runButton = new Button("Uruchom symulację");
    private final Label status = new Label("Gotowy.");

    private final TableView<String[]> metricsTable = new TableView<>();
    private final TableView<SlaSummary> slaTable = new TableView<>();

    private final BarChart<String, Number> availabilityChart =
            new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final BarChart<String, Number> outagesChart =
            new BarChart<>(new CategoryAxis(), new NumberAxis());

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setLeft(buildForm());

        TabPane tabs = new TabPane(new Tab("Wyniki", buildResultsPane()), new Tab("SLA", buildSlaPane()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.setCenter(tabs);

        stage.setTitle("Symulacja dostępności serwerowni");
        stage.setScene(new Scene(root, 1200, 720));
        stage.show();
    }

    // ------------------------------------------------------------ formularz

    private ScrollPane buildForm() {
        SystemParams d = new SystemParams();
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.setPadding(new Insets(10));
        int row = 0;

        g.add(new Label("Pamięć masowa"), 0, row);
        storageBox.getItems().setAll(StorageType.values());
        storageBox.setValue(StorageType.RAID10);
        g.add(storageBox, 1, row++);

        row = addField(g, row, "reaction", "Czas reakcji serwisu [h]", d.reactionTimeH);
        row = addField(g, row, "upsMttf", "MTTF UPS [h]", d.upsMttf);
        row = addField(g, row, "switchMttf", "MTTF przełącznika [h]", d.switchMttf);
        row = addField(g, row, "ctrlMttf", "MTTF kontrolera RAID [h]", d.ctrlMttf);
        row = addField(g, row, "serverBeta", "Serwer: beta", d.serverBeta);
        row = addField(g, row, "serverEta", "Serwer: eta [h]", d.serverEta);
        row = addField(g, row, "diskBeta", "Dysk: beta", d.diskBeta);
        row = addField(g, row, "diskEta", "Dysk: eta [h]", d.diskEta);
        row = addField(g, row, "diskRebuild", "Dysk: wymiana + odbudowa [h]", d.diskRebuildH);
        row = addField(g, row, "runs", "Liczba realizacji", 100);
        row = addField(g, row, "seed", "Ziarno generatora (seed)", 42);

        runButton.setMaxWidth(Double.MAX_VALUE);
        runButton.setOnAction(e -> runSimulation());
        g.add(runButton, 0, row++, 2, 1);
        g.add(status, 0, row, 2, 1);

        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setPrefWidth(340);
        return sp;
    }

    private int addField(GridPane g, int row, String key, String label, double defaultValue) {
        TextField tf = new TextField(formatNumber(defaultValue));
        tf.setPrefColumnCount(8);
        fields.put(key, tf);
        g.add(new Label(label), 0, row);
        g.add(tf, 1, row);
        return row + 1;
    }

    private double number(String key) {
        return Double.parseDouble(fields.get(key).getText().trim().replace(',', '.'));
    }

    /** Czyta formularz; przy błędzie pokazuje komunikat i zwraca null. */
    private Input readInput() {
        try {
            SystemParams p = new SystemParams();
            p.storage = storageBox.getValue();
            p.reactionTimeH = number("reaction");
            p.upsMttf = number("upsMttf");
            p.switchMttf = number("switchMttf");
            p.ctrlMttf = number("ctrlMttf");
            p.serverBeta = number("serverBeta");
            p.serverEta = number("serverEta");
            p.diskBeta = number("diskBeta");
            p.diskEta = number("diskEta");
            p.diskRebuildH = number("diskRebuild");
            int runs = (int) number("runs");
            if (runs < 1) throw new IllegalArgumentException("Liczba realizacji musi być >= 1");
            p.validate();
            return new Input(p, runs, (long) number("seed"));
        } catch (RuntimeException ex) {
            showError("Niepoprawne parametry: " + ex.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------ symulacja

    private void runSimulation() {
        Input in = readInput();
        if (in == null) return;

        runButton.setDisable(true);
        status.setText("Liczenie...");

        Task<Output> task = new Task<>() {
            @Override
            protected Output call() {
                List<RunResult> runs = Experiment.runMany(in.params(), in.runs(), in.seed());
                List<SlaSummary> sla = new ArrayList<>();
                for (SlaLevel level : SlaLevel.all()) {
                    SystemParams p = in.params().copy().applySla(level);
                    sla.add(SlaEvaluator.summarize(level, Experiment.runMany(p, in.runs(), in.seed())));
                }
                return new Output(runs, sla);
            }
        };
        task.setOnSucceeded(e -> {
            show(new Summary(task.getValue().runs()));
            slaTable.getItems().setAll(task.getValue().sla());
            runButton.setDisable(false);
            status.setText("Gotowe: " + in.runs() + " realizacji.");
        });
        task.setOnFailed(e -> {
            runButton.setDisable(false);
            status.setText("Błąd.");
            showError(String.valueOf(task.getException()));
        });

        Thread thread = new Thread(task, "simulation");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------ wyniki

    private VBox buildResultsPane() {
        metricsTable.getColumns().add(MainApp.<String[]>column("Miara", r -> r[0]));
        metricsTable.getColumns().add(MainApp.<String[]>column("Wartość", r -> r[1]));
        metricsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        metricsTable.setPrefHeight(260);

        availabilityChart.setTitle("Rozkład dostępności A (realizacje)");
        availabilityChart.setLegendVisible(false);
        availabilityChart.setAnimated(false);
        outagesChart.setTitle("Rozkład liczby przerw N w roku");
        outagesChart.setLegendVisible(false);
        outagesChart.setAnimated(false);

        HBox charts = new HBox(10, availabilityChart, outagesChart);
        HBox.setHgrow(availabilityChart, Priority.ALWAYS);
        HBox.setHgrow(outagesChart, Priority.ALWAYS);
        VBox.setVgrow(charts, Priority.ALWAYS);

        VBox box = new VBox(10, metricsTable, charts);
        box.setPadding(new Insets(10));
        return box;
    }

    private VBox buildSlaPane() {
        slaTable.getColumns().add(MainApp.<SlaSummary>column("Poziom", s -> s.level().name()));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("t_reakcji [h]", s -> formatNumber(s.level().reactionTimeH())));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("P(dostępność)", s -> percent(s.pAvailability())));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("P(liczba przerw)", s -> percent(s.pOutages())));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("P(maks. przerwa)", s -> percent(s.pLongestOutage())));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("P(śr. naprawa)", s -> percent(s.pMeanRepair())));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("P(wszystkie)", s -> percent(s.pAll())));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("≥ 90%?", s -> s.meetsTarget(0.9) ? "TAK" : "NIE"));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("Utrzymanie", s -> money(s.level().maintenanceCost())));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("Oczekiwana kara", s -> money(s.expectedPenalty())));
        slaTable.getColumns().add(MainApp.<SlaSummary>column("Bilans dostawcy", s -> money(s.providerBalance())));
        slaTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Label note = new Label("Każdy poziom liczony osobno z własnym czasem reakcji serwisu "
                + "(pole \"Czas reakcji\" z formularza dotyczy tylko zakładki Wyniki). "
                + "Koszty premium i ultra premium uzupełnij w SlaLevel (Rys. 7.4).");
        note.setWrapText(true);

        VBox box = new VBox(10, slaTable, note);
        box.setPadding(new Insets(10));
        VBox.setVgrow(slaTable, Priority.ALWAYS);
        return box;
    }

    private static String percent(double p) {
        return String.format("%.0f%%", p * 100);
    }

    private static String money(double v) {
        return String.format("%,.0f", v);
    }

    private void show(Summary s) {
        metricsTable.getItems().setAll(
                row("Liczba realizacji", String.valueOf(s.size())),
                row("Dostępność A (średnia)", String.format("%.5f", s.meanAvailability())),
                row("Dostępność A (minimum)", String.format("%.5f", s.minAvailability())),
                row("Liczba przerw N (średnia)", String.format("%.2f", s.meanOutages())),
                row("Najdłuższa przerwa, średnia [okresy 10 min]",
                        String.format("%.1f", s.meanLongestOutagePeriods())),
                row("Najdłuższa przerwa, maksimum [okresy 10 min]",
                        String.valueOf(s.maxLongestOutagePeriods())),
                row("MTRS [h]", formatOrDash(s.mtrsH())),
                row("MTBF [h]", formatOrDash(s.mtbfH())),
                row("MTBSI [h]", formatOrDash(s.mtbsiH())));

        // histogram dostępności: 10 równych przedziałów między minimum a maksimum
        double[] av = s.availabilities();
        double lo = Arrays.stream(av).min().orElse(0);
        double hi = Arrays.stream(av).max().orElse(1);
        if (hi - lo < 1e-9) hi = lo + 1e-6;
        int bins = 10;
        int[] counts = Summary.histogram(av, lo, hi, bins);
        double width = (hi - lo) / bins;

        XYChart.Series<String, Number> availabilitySeries = new XYChart.Series<>();
        for (int i = 0; i < bins; i++) {
            availabilitySeries.getData().add(
                    new XYChart.Data<>(String.format("%.5f", lo + (i + 0.5) * width), counts[i]));
        }
        availabilityChart.getData().setAll(List.of(availabilitySeries));

        XYChart.Series<String, Number> outagesSeries = new XYChart.Series<>();
        s.outageCountHistogram().forEach((n, c) ->
                outagesSeries.getData().add(new XYChart.Data<>(String.valueOf(n), c)));
        outagesChart.getData().setAll(List.of(outagesSeries));
    }

    // ------------------------------------------------------------ pomocnicze

    private static <T> TableColumn<T, String> column(String title, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }

    private static String[] row(String name, String value) {
        return new String[]{name, value};
    }

    private static String formatNumber(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String formatOrDash(double v) {
        return Double.isNaN(v) ? "—" : String.format("%.2f", v);
    }

    private static void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }
}
