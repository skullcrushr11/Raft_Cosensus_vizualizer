import javafx.animation.AnimationTimer;
import javafx.scene.effect.Glow;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RaftNode {
    private int id;
    private String state; // "FOLLOWER", "CANDIDATE", "LEADER"
    private int currentTerm;
    private int votedFor;
    private List<LogEntry> log;
    private int commitIndex;
    private int lastApplied;
    private Circle visualNode;
    private Text idText;
    private Group nodeGroup;
    private VBox detailsPanel;
    private long lastHeartbeat;
    private Random random;
    private long electionTimeout;
    private boolean isSelected;
    private boolean isPaused;
    private long electionStartTime;
    private boolean hasVotedInCurrentTerm;
    private long lastElectionTime;
    private Label timerLabel;
    private static final long ELECTION_TIMEOUT_MIN = 3000; // 3 seconds minimum timeout
    private static final long ELECTION_TIMEOUT_MAX = 6000; // 6 seconds maximum timeout

    // Log replication fields
    private List<Integer> nextIndex;  // For leader: next log entry to send to each follower
    private List<Integer> matchIndex; // For leader: highest log entry known to be replicated on each follower

    private RaftCluster cluster;

    private int partitionId; // Track which partition this node belongs to
    private javafx.scene.shape.Line partitionLine; // Visual line showing partition

    // ***** New field for countdown ring *****
    private Arc timeoutArc;
    // AnimationTimer for smooth updates on the arc
    private AnimationTimer timeoutTimer;
    
    public RaftNode(int id, double x, double y) {
        this.id = id;
        this.state = "FOLLOWER";
        this.currentTerm = 0;
        this.votedFor = -1;
        this.log = new ArrayList<>();
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.random = new Random();
        this.electionStartTime = System.currentTimeMillis();
        this.hasVotedInCurrentTerm = false;
        this.lastElectionTime = System.currentTimeMillis();
        this.timerLabel = new Label("Timeout: " + getRemainingTimeout() + "s");
        this.timerLabel.setStyle("-fx-text-fill: black;");

        // Initialize log replication fields
        this.nextIndex = new ArrayList<>();
        this.matchIndex = new ArrayList<>();

        // Initialize with a random timeout
        resetElectionTimeout();

        // Create visual representation
        this.visualNode = new Circle(20);
        this.visualNode.setTranslateX(x);
        this.visualNode.setTranslateY(y);

        // Create node ID text
        this.idText = new Text(String.valueOf(id));
        this.idText.setTranslateX(x - 5);
        this.idText.setTranslateY(y + 5);

        // Create node group and add visual elements
        this.nodeGroup = new Group(visualNode, idText);
        
        // ***** Initialize the countdown ring (Arc) *****
        timeoutArc = new Arc();
        timeoutArc.setCenterX(x);
        timeoutArc.setCenterY(y);
        timeoutArc.setRadiusX(28); // Slightly larger than the node's radius for a ring effect
        timeoutArc.setRadiusY(28);
        timeoutArc.setStartAngle(90);   // Starting at the top
        timeoutArc.setLength(360);      // Full circle initially
        timeoutArc.setStroke(Color.ORANGE);
        timeoutArc.setStrokeWidth(3);
        timeoutArc.setFill(null);       // No fill so only the ring is drawn
        // Add the arc to the node group (behind the node, if needed adjust order)
        nodeGroup.getChildren().add(timeoutArc);
        
        // Create details panel for node information and controls
        createDetailsPanel();

        // Make node selectable via mouse click
        this.nodeGroup.setOnMouseClicked(this::handleNodeClick);

        updateVisualState();

        this.cluster = null;
        this.partitionId = 0; // Default to partition 0 (no partition)
        this.partitionLine = null;

        // ***** Start an AnimationTimer to update the countdown ring smoothly *****
        timeoutTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateTimeoutRing();
            }
        };
        timeoutTimer.start();
    }

    private void createDetailsPanel() {
        detailsPanel = new VBox(5);
        detailsPanel.setStyle("-fx-background-color: white; -fx-border-color: black; -fx-padding: 10;");
        detailsPanel.setVisible(false);

        // Node details
        Label stateLabel = new Label("State: " + state);
        Label termLabel = new Label("Current Term: " + currentTerm);
        Label votedLabel = new Label("Voted For: " + (votedFor == -1 ? "null" : votedFor));
        Label commitLabel = new Label("Commit Index: " + commitIndex);

        // Add partition ID label
        Label partitionIdLabel = new Label("Partition ID: " + partitionId);

        // Log entries display
        VBox logEntriesBox = new VBox(2);
        logEntriesBox.getChildren().add(new Label("Log Entries:"));
        for (LogEntry entry : log) {
            logEntriesBox.getChildren().add(new Label(entry.toString()));
        }

        // Control buttons
        HBox buttonRow1 = new HBox(5);
        Button stopButton = new Button("Stop");
        Button resumeButton = new Button("Resume");
        Button restartButton = new Button("Restart");

        HBox buttonRow2 = new HBox(5);
        Button timeoutButton = new Button("Timeout");
        Button requestButton = new Button("Request");
        Button addLogButton = new Button("Add Log");

        // Button actions
        stopButton.setOnAction(e -> {
            isPaused = true;
            updateDetailsPanel();
        });

        resumeButton.setOnAction(e -> {
            isPaused = false;
            updateDetailsPanel();
        });

        restartButton.setOnAction(e -> {
            resetNode();
            updateDetailsPanel();
        });

        timeoutButton.setOnAction(e -> {
            simulateTimeout();
            updateDetailsPanel();
        });

        requestButton.setOnAction(e -> {
            requestVote();
            updateDetailsPanel();
        });

        addLogButton.setOnAction(e -> {
            appendEntry(new LogEntry(currentTerm, log.size(), "Command " + log.size()));
            updateDetailsPanel();
        });

        buttonRow1.getChildren().addAll(stopButton, resumeButton, restartButton);
        buttonRow2.getChildren().addAll(timeoutButton, requestButton, addLogButton);

        detailsPanel.getChildren().addAll(
            stateLabel, termLabel, votedLabel, commitLabel, partitionIdLabel,
            logEntriesBox,
            buttonRow1, buttonRow2
        );

        // Add timer label to details panel
        detailsPanel.getChildren().add(timerLabel);
    }

    private void handleNodeClick(MouseEvent event) {
        isSelected = !isSelected;
        if (isSelected) {
            visualNode.setStroke(Color.RED);
            visualNode.setStrokeWidth(2);
            detailsPanel.setVisible(true);
        } else {
            visualNode.setStroke(Color.BLACK);
            visualNode.setStrokeWidth(1);
            detailsPanel.setVisible(false);
        }
        updateDetailsPanel();
    }

    public void updateDetailsPanel() {
        if (detailsPanel != null) {
            Label stateLabel = (Label) detailsPanel.getChildren().get(0);
            Label termLabel = (Label) detailsPanel.getChildren().get(1);
            Label votedLabel = (Label) detailsPanel.getChildren().get(2);
            Label commitLabel = (Label) detailsPanel.getChildren().get(3);

            stateLabel.setText("State: " + state);
            termLabel.setText("Current Term: " + currentTerm);
            votedLabel.setText("Voted For: " + (votedFor == -1 ? "null" : votedFor));
            commitLabel.setText("Commit Index: " + commitIndex);

            // Update partition ID label
            Label partitionIdLabel = (Label) detailsPanel.getChildren().get(4);
            partitionIdLabel.setText("Partition ID: " + partitionId);

            // Update log entries
            VBox logEntriesBox = (VBox) detailsPanel.getChildren().get(5);
            logEntriesBox.getChildren().clear();
            logEntriesBox.getChildren().add(new Label("Log Entries:"));
            for (LogEntry entry : log) {
                Label entryLabel = new Label(entry.toString());
                if (entry.getIndex() <= commitIndex) {
                    entryLabel.setStyle("-fx-text-fill: green;");
                }
                logEntriesBox.getChildren().add(entryLabel);
            }

            // Update button states
            Button stopButton = (Button) ((HBox) detailsPanel.getChildren().get(6)).getChildren().get(0);
            Button resumeButton = (Button) ((HBox) detailsPanel.getChildren().get(6)).getChildren().get(1);

            stopButton.setDisable(isPaused);
            resumeButton.setDisable(!isPaused);

            // Update timer label with seconds
            timerLabel.setText("Timeout: " + getRemainingTimeout() + "s");
        }
    }

    public void updateVisualState() {
        if (isPaused) {
            visualNode.setFill(Color.GRAY);
        } else {
            switch (state) {
                case "FOLLOWER":
                    visualNode.setFill(Color.LIGHTBLUE);
                    visualNode.setStroke(Color.DARKBLUE);
                    break;
                case "CANDIDATE":
                    visualNode.setFill(Color.GOLD);
                    visualNode.setStroke(Color.DARKGOLDENROD);
                    break;
                case "LEADER":
                    visualNode.setFill(Color.LIMEGREEN);
                    visualNode.setStroke(Color.DARKGREEN);
                    visualNode.setEffect(new Glow(0.7));
                    break;
            }
            visualNode.setStrokeWidth(3);
        }

        // Update partition visualization
        if (partitionId > 0) {
            Text partitionText = new Text(String.valueOf(partitionId));
            partitionText.setX(visualNode.getCenterX() + 15);
            partitionText.setY(visualNode.getCenterY() - 15);
            partitionText.setFill(Color.RED);
            partitionText.setStyle("-fx-font-weight: bold;");

            // Remove old partition text if exists
            nodeGroup.getChildren().removeIf(node -> node instanceof Text && node != idText);
            nodeGroup.getChildren().add(partitionText);
        } else {
            // Remove partition text if no longer partitioned
            nodeGroup.getChildren().removeIf(node -> node instanceof Text && node != idText);
        }

        updateDetailsPanel();
    }

    public void startElection() {
        if (isPaused()) return;

        System.out.println("Node " + id + " starting election for term " + (currentTerm + 1));
        state = "CANDIDATE";
        currentTerm++;
        votedFor = id;
        hasVotedInCurrentTerm = true;
        lastElectionTime = System.currentTimeMillis();
        resetElectionTimeout();
        updateVisualState();
    }

    public void becomeLeader() {
        System.out.println("Node " + id + " becoming leader for term " + currentTerm);
        state = "LEADER";
        initializeLeaderState();
        updateVisualState();
    }

    private void initializeLeaderState() {
        nextIndex.clear();
        matchIndex.clear();
        int lastLogIndex = getLastLogIndex();
        for (int i = 0; i < 5; i++) { // Assuming max 5 nodes
            nextIndex.add(lastLogIndex + 1);
            matchIndex.add(0);
        }
    }

    public void becomeFollower(int term) {
        if (term > currentTerm) {
            System.out.println("Node " + id + " becoming follower for term " + term);
            state = "FOLLOWER";
            currentTerm = term;
            votedFor = -1;
            hasVotedInCurrentTerm = false;
            resetElectionTimeout();
            updateVisualState();
        }
    }

    public void appendEntry(LogEntry entry) {
        log.add(entry);
        System.out.println("Node " + id + " appended entry: " + entry);
        updateDetailsPanel();
    }

    public boolean appendEntries(int leaderTerm, int leaderId, int prevLogIndex, int prevLogTerm,
                                 List<LogEntry> entries, int leaderCommit) {
        // If term is higher, update term and become follower
        if (leaderTerm > currentTerm) {
            System.out.println("Node " + id + " stepping down: received term " + leaderTerm + " > current term " + currentTerm);
            stepDown(leaderTerm);
        }
        
        // Reply false if term < currentTerm
        if (leaderTerm < currentTerm) {
            System.out.println("Node " + id + " rejecting AppendEntries: leader term " + leaderTerm + " < current term " + currentTerm);
            return false;
        }
        
        // ***** NEW CODE: If the node is a candidate and it receives a heartbeat (AppendEntries)
        // from another node with the same term, step down to follower.
        if (state.equals("CANDIDATE") && leaderTerm == currentTerm && leaderId != this.id) {
            System.out.println("Node " + id + " stepping down as candidate upon receiving heartbeat from leader " + leaderId);
            stepDown(leaderTerm);
        }
        
        // Reset election timeout since we received valid RPC from current leader
        lastHeartbeat = System.currentTimeMillis();
        
        // Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm
        if (prevLogIndex >= log.size()) {
            System.out.println("Node " + id + " rejecting AppendEntries: prevLogIndex " + prevLogIndex + " >= log size " + log.size());
            return false;
        }
        
        if (prevLogIndex >= 0 && log.get(prevLogIndex).getTerm() != prevLogTerm) {
            System.out.println("Node " + id + " rejecting AppendEntries: term mismatch at index " + prevLogIndex);
            return false;
        }
        
        // Delete conflicting entries and append new ones
        if (entries != null && !entries.isEmpty()) {
            while (log.size() > prevLogIndex + 1) {
                log.remove(log.size() - 1);
            }
            for (LogEntry entry : entries) {
                log.add(entry);
                System.out.println("Node " + id + " appended entry: " + entry);
            }
        }
        
        // Update commit index
        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, log.size() - 1);
            System.out.println("Node " + id + " updated commit index to " + commitIndex);
        }
        
        updateDetailsPanel();
        return true;
    }

    public void updateNextIndex(int followerId, int newNextIndex) {
        if (followerId >= 0 && followerId < nextIndex.size()) {
            nextIndex.set(followerId, newNextIndex);
        }
    }

    public void updateMatchIndex(int followerId, int newMatchIndex) {
        if (followerId >= 0 && followerId < matchIndex.size()) {
            matchIndex.set(followerId, newMatchIndex);
        }
    }

    public void updateCommitIndex() {
        if (!state.equals("LEADER")) return;
        for (int i = log.size() - 1; i > commitIndex; i--) {
            if (log.get(i).getTerm() != currentTerm) continue;
            int count = 1; // Count self
            for (int j = 0; j < matchIndex.size(); j++) {
                if (matchIndex.get(j) >= i) {
                    count++;
                }
            }
            if (count > 5 / 2) { // Assuming 5 nodes total
                commitIndex = i;
                System.out.println("Node " + id + " updated commit index to " + commitIndex);
                break;
            }
        }
        updateDetailsPanel();
    }

    public Group getVisualNode() {
        return nodeGroup;
    }

    public VBox getDetailsPanel() {
        return detailsPanel;
    }

    public void setDetailsPanelPosition(double x, double y) {
        detailsPanel.setTranslateX(x);
        detailsPanel.setTranslateY(y);
    }

    public int getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public int getVotedFor() {
        return votedFor;
    }

    public void setLastHeartbeat(long time) {
        this.lastHeartbeat = time;
        resetElectionTimeout();
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public int getElectionTimeout() {
        return (int) electionTimeout;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (!paused) {
            // Check if there is already a leader in the cluster.
            RaftCluster cluster = getCluster();
            if (cluster != null) {
                RaftNode currentLeader = cluster.getCurrentLeader();
                // If a valid leader exists, and this node is in a non-FOLLOWER state, step down.
                if (currentLeader != null && currentLeader != this &&
                    (state.equals("LEADER") || state.equals("CANDIDATE"))) {
                    System.out.println("Node " + id + " detected current leader " + currentLeader.getId() +
                                       ", transitioning to follower on resume");
                    becomeFollower(currentLeader.getCurrentTerm());
                }
            }
            resetElectionTimeout();
            updateVisualState();
        } else {
            this.lastHeartbeat = System.currentTimeMillis() - electionTimeout - 1000;
            visualNode.setFill(Color.GRAY);
        }
        updateDetailsPanel();
    }
    

    public void checkForNewLeader() {
        if (state.equals("LEADER")) {
            System.out.println("Node " + id + " was a leader, transitioning to follower on resume");
            state = "FOLLOWER";
            votedFor = -1;
            hasVotedInCurrentTerm = false;
            updateVisualState();
        }
    }

    public void resetElectionTimeout() {
        this.electionStartTime = System.currentTimeMillis();
        this.lastHeartbeat = System.currentTimeMillis();
        this.electionTimeout = ELECTION_TIMEOUT_MIN + random.nextInt((int)(ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN));
        System.out.println("Node " + id + " reset timeout to " + (electionTimeout / 1000) + " seconds");
        updateDetailsPanel();
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void resetNode() {
        state = "FOLLOWER";
        currentTerm = 0;
        votedFor = -1;
        log.clear();
        commitIndex = 0;
        lastApplied = 0;
        lastHeartbeat = System.currentTimeMillis();
        resetElectionTimeout();
        isPaused = false;
        hasVotedInCurrentTerm = false;
        updateVisualState();
    }

    public void simulateTimeout() {
        lastHeartbeat = System.currentTimeMillis() - electionTimeout - 10;
    }

    public void requestVote() {
        if (state.equals("FOLLOWER") && !isPaused) {
            startElection();
        }
    }

    public void setPosition(double x, double y) {
        visualNode.setTranslateX(x);
        visualNode.setTranslateY(y);
        idText.setTranslateX(x - 5);
        idText.setTranslateY(y + 5);
        if (timeoutArc != null) {
            timeoutArc.setCenterX(x);
            timeoutArc.setCenterY(y);
        }
    }

    public boolean hasVotedInCurrentTerm() {
        return hasVotedInCurrentTerm;
    }

    public void setHasVotedInCurrentTerm(boolean hasVoted) {
        this.hasVotedInCurrentTerm = hasVoted;
    }

    public long getElectionStartTime() {
        return electionStartTime;
    }

    public boolean isLogUpToDate(int candidateLastLogIndex, int candidateLastLogTerm) {
        if (log.isEmpty()) {
            return true;
        }
        LogEntry lastEntry = log.get(log.size() - 1);
        return candidateLastLogTerm > lastEntry.getTerm() ||
               (candidateLastLogTerm == lastEntry.getTerm() && candidateLastLogIndex >= log.size() - 1);
    }

    public void voteForCandidate(int candidateId, int candidateTerm, int candidateLastLogIndex, int candidateLastLogTerm) {
        if (candidateTerm > currentTerm) {
            System.out.println("Node " + id + " stepping down: received term " + candidateTerm + " > current term " + currentTerm);
            stepDown(candidateTerm);
        }
        if (candidateTerm < currentTerm) {
            System.out.println("Node " + id + " rejecting vote for node " + candidateId + " (term " + candidateTerm + " < " + currentTerm + ")");
            return;
        }
        if ((!hasVotedInCurrentTerm || votedFor == candidateId) &&
            isLogUpToDate(candidateLastLogIndex, candidateLastLogTerm)) {
            System.out.println("Node " + id + " voting for node " + candidateId + " in term " + candidateTerm);
            votedFor = candidateId;
            hasVotedInCurrentTerm = true;
            lastHeartbeat = System.currentTimeMillis();
            updateDetailsPanel();
        } else {
            System.out.println("Node " + id + " not voting for node " + candidateId + " (already voted or log not up-to-date)");
        }
    }

    public void stepDown(int newTerm) {
        currentTerm = newTerm;
        state = "FOLLOWER";
        votedFor = -1;
        hasVotedInCurrentTerm = false;
        resetElectionTimeout();
        updateVisualState();
        System.out.println("Node " + id + " stepped down to follower with term " + newTerm);
    }

    public boolean hasReceivedHeartbeat() {
        if (isPaused) return false;
        long currentTime = System.currentTimeMillis();
        return currentTime - lastHeartbeat < electionTimeout;
    }

    public boolean shouldStartElection() {
        if (isPaused) return false;
        return (state.equals("FOLLOWER") || state.equals("CANDIDATE")) &&
               !hasReceivedHeartbeat();
    }

    public boolean hasElectionTimedOut() {
        if (isPaused()) return false;
        long currentTime = System.currentTimeMillis();
        boolean timedOut = currentTime - lastElectionTime > electionTimeout;
        if (timedOut) {
            System.out.println("Node " + id + " election timed out");
        }
        return timedOut;
    }

    public int getLastLogIndex() {
        return log.isEmpty() ? 0 : log.size() - 1;
    }

    public int getLastLogTerm() {
        return log.isEmpty() ? 0 : log.get(log.size() - 1).getTerm();
    }

    public long getLastElectionTime() {
        return lastElectionTime;
    }

    public void setLastElectionTime(long time) {
        this.lastElectionTime = time;
    }

    public long getRemainingTimeout() {
        if (isPaused) return 0;
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastHeartbeat;
        long timeout = electionTimeout - elapsed;
        return Math.max(0, timeout / 1000);
    }

    public List<LogEntry> getLog() {
        return log;
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    public int getNextIndex(int followerId) {
        if (followerId >= 0 && followerId < nextIndex.size()) {
            return nextIndex.get(followerId);
        }
        return 0;
    }

    public int getMatchIndex(int followerId) {
        if (followerId >= 0 && followerId < matchIndex.size()) {
            return matchIndex.get(followerId);
        }
        return 0;
    }

    public void setNextIndex(int followerId, int value) {
        if (followerId >= 0 && followerId < nextIndex.size()) {
            nextIndex.set(followerId, value);
        }
    }

    public void setMatchIndex(int followerId, int value) {
        if (followerId >= 0 && followerId < matchIndex.size()) {
            matchIndex.set(followerId, value);
        }
    }

    public void setCluster(RaftCluster cluster) {
        this.cluster = cluster;
    }

    public RaftCluster getCluster() {
        return cluster;
    }

    public void setPartitionId(int partitionId) {
        this.partitionId = partitionId;
        updateVisualState();
    }

    public int getPartitionId() {
        return partitionId;
    }

    public void setPartitionLine(javafx.scene.shape.Line line) {
        this.partitionLine = line;
    }

    public javafx.scene.shape.Line getPartitionLine() {
        return partitionLine;
    }

    public boolean canCommunicateWith(RaftNode other) {
        if (other == null) return false;
        if (partitionId == 0 && other.getPartitionId() == 0) {
            return true;
        }
        if (partitionId != other.getPartitionId()) {
            return false;
        }
        return true;
    }
    
    // ***** New method to update the countdown ring *****
    private void updateTimeoutRing() {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastHeartbeat;
        double fraction = (double)(electionTimeout - elapsed) / electionTimeout;
        fraction = Math.max(0, Math.min(1, fraction)); // Clamp fraction between 0 and 1
        timeoutArc.setLength(360 * fraction);
    }
}
