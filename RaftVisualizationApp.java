import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Queue;
import java.util.LinkedList;

public class RaftVisualizationApp extends Application {
    private RaftCluster cluster;
    private Pane visualizationPane;
    private Label statusLabel;
    private TextField logEntryField;
    private Random random;
    private BorderPane root;
    
    @Override
    public void start(Stage primaryStage) {
        random = new Random();
        
        root = new BorderPane();
        
        statusLabel = new Label("Raft Cluster Status: Running");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        visualizationPane = new Pane();
        visualizationPane.setStyle("-fx-background-color: white; -fx-border-color: black;");
        visualizationPane.setPrefSize(800, 600);
        
        HBox controls = new HBox(10);
        Button addNodeButton = new Button("Add Node");
        Button removeNodeButton = new Button("Remove Last Node");
        Button partitionButton = new Button("Simulate Partition");
        Button healButton = new Button("Heal Partition");
        Button clearButton = new Button("Clear All");
        
        HBox logControls = new HBox(10);
        logEntryField = new TextField();
        logEntryField.setPromptText("Enter log entry command");
        Button addLogButton = new Button("Add Log Entry");
        
        addNodeButton.setOnAction(e -> {
            cluster.addNode(0, 0);
            updateStatus("Node added");
        });
        
        removeNodeButton.setOnAction(e -> {
            List<RaftNode> nodes = cluster.getNodes();
            if (!nodes.isEmpty()) {
                cluster.removeNode(nodes.get(nodes.size() - 1).getId());
                updateStatus("Last node removed");
            }
        });
        
        partitionButton.setOnAction(e -> {
            List<RaftNode> nodes = cluster.getNodes();
            if (nodes.size() >= 3) {
                cluster.simulatePartition(Arrays.asList(0, 1));
                updateStatus("Network partition simulated");
            } else {
                updateStatus("Need at least 3 nodes to simulate partition");
            }
        });
        
        healButton.setOnAction(e -> {
            cluster.healPartition();
            updateStatus("Network partition healed");
        });
        
        clearButton.setOnAction(e -> {
            List<RaftNode> nodes = cluster.getNodes();
            for (int i = nodes.size() - 1; i >= 0; i--) {
                cluster.removeNode(nodes.get(i).getId());
            }
            updateStatus("All nodes cleared");
        });
        
        addLogButton.setOnAction(e -> {
            String command = logEntryField.getText().trim();
            if (!command.isEmpty()) {
                addLogEntry(command);
                logEntryField.clear();
            }
        });
        
        controls.getChildren().addAll(addNodeButton, removeNodeButton, partitionButton, healButton, clearButton);
        logControls.getChildren().addAll(logEntryField, addLogButton);
        
        createControlPanel();
        
        root.setTop(statusLabel);
        root.setCenter(visualizationPane);
        root.setBottom(new VBox(10, controls, logControls));
        
        cluster = new RaftCluster(visualizationPane);
        
        for (int i = 0; i < 3; i++) {
            cluster.addNode(0, 0);
        }
        
        Scene scene = new Scene(root);
        primaryStage.setTitle("Raft Consensus Visualization");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    private void createControlPanel() {
        VBox controlPanel = new VBox(10);
        controlPanel.setPadding(new Insets(10));
        controlPanel.setStyle("-fx-background-color: #f0f0f0;");
        
        Label partitionLabel = new Label("Partition Controls");
        partitionLabel.setStyle("-fx-font-weight: bold;");
        
        HBox partitionControls = new HBox(10);
        partitionControls.setAlignment(Pos.CENTER_LEFT);
        
        Label nodeIdLabel = new Label("Node ID:");
        TextField nodeIdField = new TextField();
        nodeIdField.setPrefWidth(50);
        
        Label partitionIdLabel = new Label("Partition ID:");
        TextField partitionIdField = new TextField();
        partitionIdField.setPrefWidth(50);
        
        Button setPartitionButton = new Button("Set Partition");
        setPartitionButton.setOnAction(e -> {
            try {
                int nodeId = Integer.parseInt(nodeIdField.getText());
                int partitionId = Integer.parseInt(partitionIdField.getText());
                cluster.setNodePartition(nodeId, partitionId);
                updateStatus("Node " + nodeId + " assigned to partition " + partitionId);
            } catch (NumberFormatException ex) {
                updateStatus("Invalid node ID or partition ID");
            }
        });
        
        Button healPartitionButton = new Button("Heal Partition");
        healPartitionButton.setOnAction(e -> {
            cluster.healPartition();
            updateStatus("Partition healed");
        });
        
        partitionControls.getChildren().addAll(
            nodeIdLabel, nodeIdField,
            partitionIdLabel, partitionIdField,
            setPartitionButton,
            healPartitionButton
        );
        
        controlPanel.getChildren().addAll(partitionLabel, partitionControls);
        
        root.setLeft(controlPanel);
    }
    
    private void addLogEntry(String commandText) {
        RaftNode leader = cluster.getCurrentLeader();
        
        if (leader != null) {
            CommandInvoker invoker = new CommandInvoker();
            invoker.addCommand(new AddLogEntryCommand(commandText));
            invoker.executeCommands(leader);
            updateStatus("Log entry added: " + commandText);
        } else {
            updateStatus("No active leader available to add log entry");
        }
    }
    
    private void updateStatus(String message) {
        statusLabel.setText("Raft Cluster Status: " + message);
    }
    
    @Override
    public void stop() {
        if (cluster != null) {
            cluster.stop();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
    
    // ==============================
    // Inner Classes for Command Pattern
    // ==============================
    
    public interface Command {
        void execute(RaftNode node);
    }
    
    public static class AddLogEntryCommand implements Command {
        private String commandText;
        
        public AddLogEntryCommand(String commandText) {
            this.commandText = commandText;
        }
        
        @Override
        public void execute(RaftNode node) {
            LogEntry entry = new LogEntry(node.getCurrentTerm(), node.getLog().size(), commandText);
            node.appendEntry(entry);
            System.out.println("Executing AddLogEntryCommand: " + commandText);
        }
    }
    public static class StartElectionCommand implements Command {
        @Override
        public void execute(RaftNode node) {
            node.startElection();
            System.out.println("Executing StartElectionCommand on node " + node.getId());
        }
    }
    
    public static class CommandInvoker {
        private Queue<Command> commandQueue = new LinkedList<>();
        
        public void addCommand(Command command) {
            commandQueue.offer(command);
        }
        
        public void executeCommands(RaftNode node) {
            while (!commandQueue.isEmpty()) {
                Command command = commandQueue.poll();
                command.execute(node);
            }
        }
    }
}
