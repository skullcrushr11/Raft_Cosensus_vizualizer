import javafx.animation.AnimationTimer;
import javafx.animation.PathTransition;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.paint.Color;
import java.util.Map;
import java.util.HashMap;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

public class RaftCluster {
    private List<RaftNode> nodes;
    private Pane visualizationPane;
    private AnimationTimer timer;
    private long lastHeartbeatTime;
    private Random random;
    private List<Circle> messageCircles;
    private RaftNode currentLeader; // Track the current active leader
    private List<javafx.scene.shape.Line> partitionLines;
    private Map<Integer, List<RaftNode>> partitions; // Track nodes in each partition
    
    public RaftCluster(Pane visualizationPane) {
        this.nodes = new ArrayList<>();
        this.visualizationPane = visualizationPane;
        this.lastHeartbeatTime = System.currentTimeMillis();
        this.random = new Random();
        this.messageCircles = new ArrayList<>();
        this.currentLeader = null;
        this.partitionLines = new ArrayList<>();
        this.partitions = new HashMap<>();
        
        // Initialize visualization timer
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateCluster();
            }
        };
        timer.start();
    }
    
    public void addNode(double x, double y) {
        // If x and y are 0, calculate position automatically
        if (x == 0 && y == 0) {
            calculateNodePosition();
        }
        
        RaftNode node = new RaftNode(nodes.size(), x, y);
        node.setCluster(this);
        nodes.add(node);
        visualizationPane.getChildren().add(node.getVisualNode());
        visualizationPane.getChildren().add(node.getDetailsPanel());
        
        // Position details panel
        node.setDetailsPanelPosition(x + 30, y - 50);
        
        // Redistribute nodes if needed
        if (nodes.size() > 1) {
            redistributeNodes();
        }
    }
    
    private void calculateNodePosition() {
        // This will be called by redistributeNodes
    }
    
    private void redistributeNodes() {
        int nodeCount = nodes.size();
        if (nodeCount <= 1) return;
    
        double paneWidth = visualizationPane.getWidth();
        double paneHeight = visualizationPane.getHeight();
        if (paneWidth <= 0 || paneHeight <= 0) {
            paneWidth = visualizationPane.getPrefWidth();
            paneHeight = visualizationPane.getPrefHeight();
        }
    
        double centerX = paneWidth / 2;
        double centerY = paneHeight / 2;
        double radius = Math.min(centerX, centerY) - 100;
    
        for (int i = 0; i < nodeCount; i++) {
            double angle = 2 * Math.PI * i / nodeCount;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
    
            nodes.get(i).setPosition(x, y);
            nodes.get(i).setDetailsPanelPosition(x + 30, y - 50);
        }
        drawConnectionLines();
    }
    
    public void removeNode(int id) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getId() == id) {
                visualizationPane.getChildren().remove(nodes.get(i).getVisualNode());
                visualizationPane.getChildren().remove(nodes.get(i).getDetailsPanel());
                nodes.remove(i);
                
                // Redistribute remaining nodes
                redistributeNodes();
                break;
            }
        }
    }
    
    // factory
    private void visualizeMessage(RaftNode from, RaftNode to, String messageType, boolean success) {
        // creating message based on type
        Message message = MessageFactory.createMessage(messageType, success);
        Node messageNode = message.getVisualNode();
        
        Circle fromCircle = (Circle) from.getVisualNode().getChildren().get(0);
        Circle toCircle = (Circle) to.getVisualNode().getChildren().get(0);
        double startX = fromCircle.getCenterX() + fromCircle.getTranslateX();
        double startY = fromCircle.getCenterY() + fromCircle.getTranslateY();
        double endX = toCircle.getCenterX() + toCircle.getTranslateX();
        double endY = toCircle.getCenterY() + toCircle.getTranslateY();
    
        Path path = new Path();
        path.getElements().add(new MoveTo(startX, startY));
        path.getElements().add(new LineTo(endX, endY));
    
        PathTransition transition = new PathTransition(Duration.millis(700), path, messageNode);
        transition.setOnFinished(e -> visualizationPane.getChildren().remove(messageNode));
    
        visualizationPane.getChildren().add(messageNode);
        transition.play();
    }
    
    // Other existing methods remain unchanged.
    
    private void drawConnectionLines() {
        visualizationPane.getChildren().removeIf(node ->
            node instanceof javafx.scene.shape.Line &&
            ((javafx.scene.shape.Line) node).getStroke().equals(Color.LIGHTGRAY)
        );
        
        for (RaftNode from : nodes) {
            for (RaftNode to : nodes) {
                if (from != to && from.canCommunicateWith(to)) {
                    if (from.getPartitionId() == to.getPartitionId()) {
                        javafx.scene.shape.Line line = new javafx.scene.shape.Line();
                        Circle circleFrom = (Circle) from.getVisualNode().getChildren().get(0);
                        Circle circleTo = (Circle) to.getVisualNode().getChildren().get(0);
    
                        line.setStartX(circleFrom.getTranslateX());
                        line.setStartY(circleFrom.getTranslateY());
                        line.setEndX(circleTo.getTranslateX());
                        line.setEndY(circleTo.getTranslateY());
                        line.setStroke(Color.LIGHTGRAY);
                        line.setStrokeWidth(1.0);
                        line.getStrokeDashArray().addAll(4.0, 4.0);
    
                        visualizationPane.getChildren().add(line);
                    }
                }
            }
        }
    }
    
    private void updateCluster() {
        long currentTime = System.currentTimeMillis();
        
        // Find current leaders in each partition
        Map<Integer, RaftNode> partitionLeaders = new HashMap<>();
        for (RaftNode node : nodes) {
            if (node.getState().equals("LEADER") && !node.isPaused()) {
                int partitionId = node.getPartitionId();
                partitionLeaders.put(partitionId, node);
            }
        }
        
        //  Process each partition's leader
        for (Map.Entry<Integer, RaftNode> entry : partitionLeaders.entrySet()) {
            RaftNode leader = entry.getValue();
            // Update current leader reference if needed
            if (currentLeader == null || currentLeader.getPartitionId() == entry.getKey()) {
                currentLeader = leader;
                System.out.println("Partition " + entry.getKey() + " leader: Node " + leader.getId());
            }
            // Send heartbeats and replicate logs every 1 second
            if (currentTime - lastHeartbeatTime > 1000) {
                System.out.println("Leader " + leader.getId() + " sending heartbeats in partition " + entry.getKey());
                sendHeartbeats(leader);
                replicateLog(leader);
            }
        }
        
        // Step 3: Update heartbeat timer
        if (currentTime - lastHeartbeatTime > 1000) {
            lastHeartbeatTime = currentTime;
        }
        
        // Step 4: Handle elections in partitions without leaders
        Map<Integer, Boolean> partitionHasLeader = new HashMap<>();
        for (RaftNode node : nodes) {
            int partitionId = node.getPartitionId();
            boolean hasLeader = partitionLeaders.containsKey(partitionId);
            partitionHasLeader.put(partitionId, hasLeader);
            
            // Start election if partition has no leader and node times out
            if (!hasLeader && node.shouldStartElection()) {
                System.out.println("Node " + node.getId() + " in partition " + partitionId + " starting election");
                startElection(node);
            }
        }
        
        // Step 5: Update UI for all nodes
        for (RaftNode node : nodes) {
            node.updateDetailsPanel();
        }
    }
    
    private void replicateLog(RaftNode leader) {
        if (leader == null || leader.isPaused()) return;
        
        for (RaftNode follower : nodes) {
            if (follower != leader && !follower.isPaused() && leader.canCommunicateWith(follower)) {
                int nextIndex = leader.getNextIndex(follower.getId());
                int matchIndex = leader.getMatchIndex(follower.getId());
                
                List<LogEntry> entriesToSend = new ArrayList<>();
                List<LogEntry> leaderLog = leader.getLog();
                
                if (!leaderLog.isEmpty() && nextIndex < leaderLog.size()) {
                    entriesToSend.addAll(leaderLog.subList(nextIndex, leaderLog.size()));
                }
                
                int prevLogTerm = 0;
                if (nextIndex > 0 && !leaderLog.isEmpty()) {
                    prevLogTerm = leaderLog.get(nextIndex - 1).getTerm();
                }
                
                boolean success = follower.appendEntries(
                    leader.getCurrentTerm(),
                    leader.getId(),
                    nextIndex - 1,
                    prevLogTerm,
                    entriesToSend,
                    leader.getCommitIndex()
                );
                
                visualizeMessage(leader, follower, "AppendEntries", success);
                
                if (success) {
                    leader.setMatchIndex(follower.getId(), nextIndex + entriesToSend.size() - 1);
                    leader.setNextIndex(follower.getId(), nextIndex + entriesToSend.size());
                } else {
                    leader.setNextIndex(follower.getId(), Math.max(0, nextIndex - 1));
                }
            }
        }
        leader.updateCommitIndex();
    }
    
    private void sendHeartbeats(RaftNode leader) {
        if (leader == null || leader.isPaused()) return;
        
        for (RaftNode follower : nodes) {
            if (follower != leader && !follower.isPaused() && leader.canCommunicateWith(follower)) {
                visualizeMessage(leader, follower, "Heartbeat", true);
                follower.setLastHeartbeat(System.currentTimeMillis());
                if (follower.getCurrentTerm() < leader.getCurrentTerm()) {
                    follower.becomeFollower(leader.getCurrentTerm());
                }
            }
        }
    }
    
    private void startElection(RaftNode candidate) {
        if (candidate.isPaused()) return;
        
        System.out.println("Starting election for node " + candidate.getId() + 
                          " (term: " + candidate.getCurrentTerm() + 
                          ", lastLogIndex: " + candidate.getLastLogIndex() + 
                          ", lastLogTerm: " + candidate.getLastLogTerm() + ")");
        
        candidate.startElection();
        int votes = 1; // Vote for self
        int nodesInPartition = 0;
        int candidateLastLogIndex = candidate.getLastLogIndex();
        int candidateLastLogTerm = candidate.getLastLogTerm();
        
        for (RaftNode node : nodes) {
            if (node != candidate && !node.isPaused() && candidate.canCommunicateWith(node)) {
                nodesInPartition++;
                
                boolean logIsUpToDate = true;
                if (!node.getLog().isEmpty()) {
                    int nodeLastIndex = node.getLastLogIndex();
                    int nodeLastTerm = node.getLastLogTerm();
                    
                    if (nodeLastTerm > candidateLastLogTerm || 
                        (nodeLastTerm == candidateLastLogTerm && nodeLastIndex > candidateLastLogIndex)) {
                        System.out.println("Node " + candidate.getId() + " can't become leader: " +
                                         "Node " + node.getId() + " has more up-to-date log " +
                                         "(term: " + nodeLastTerm + ", index: " + nodeLastIndex + ")");
                        logIsUpToDate = false;
                        continue;
                    }
                }
                
                visualizeMessage(candidate, node, "RequestVote", true);
                node.voteForCandidate(
                    candidate.getId(),
                    candidate.getCurrentTerm(),
                    candidateLastLogIndex,
                    candidateLastLogTerm
                );
                
                if (node.getVotedFor() == candidate.getId()) {
                    votes++;
                    visualizeMessage(node, candidate, "Vote", true);
                }
            }
        }
        
        int majority = (nodesInPartition + 1) / 2 + 1;
        
        System.out.println("Node " + candidate.getId() + " received " + votes + 
                          " votes, needed " + majority + " for majority");
        
        if (votes > majority) {
            candidate.becomeLeader();
            candidate.setLastHeartbeat(System.currentTimeMillis());
            for (RaftNode node : nodes) {
                if (node != candidate && !node.isPaused() && candidate.canCommunicateWith(node)) {
                    if (node.getCurrentTerm() < candidate.getCurrentTerm()) {
                        node.becomeFollower(candidate.getCurrentTerm());
                    }
                }
            }
        } else {
            candidate.becomeFollower(candidate.getCurrentTerm());
            System.out.println("Node " + candidate.getId() + " failed to get majority, reverting to follower");
        }
    }
    
    public void simulatePartition(List<Integer> partitionedNodes) {
        for (javafx.scene.shape.Line line : partitionLines) {
            visualizationPane.getChildren().remove(line);
        }
        partitionLines.clear();
    
        for (RaftNode node : nodes) {
            if (partitionedNodes.contains(node.getId())) {
                node.setPartitionId(1);
            } else {
                node.setPartitionId(2);
            }
        }
    
        updatePartitionVisualization();
        drawConnectionLines();
    }
    
    public void setNodePartition(int nodeId, int partitionId) {
        RaftNode node = null;
        for (RaftNode n : nodes) {
            if (n.getId() == nodeId) {
                node = n;
                break;
            }
        }
    
        if (node != null) {
            if (node == currentLeader) {
                for (RaftNode follower : nodes) {
                    if (follower != node && follower.getPartitionId() != partitionId) {
                        follower.simulateTimeout();
                    }
                }
                currentLeader = null;
            }
            
            node.setPartitionId(partitionId);
            if (node.getState().equals("LEADER")) {
                node.becomeFollower(node.getCurrentTerm());
            }
            updatePartitionVisualization();
            drawConnectionLines();
        }
    }
    
    private void updatePartitionVisualization() {
        for (javafx.scene.shape.Line line : partitionLines) {
            visualizationPane.getChildren().remove(line);
        }
        partitionLines.clear();
        
        for (int partitionId1 : partitions.keySet()) {
            List<RaftNode> partition1 = partitions.get(partitionId1);
            for (int partitionId2 : partitions.keySet()) {
                if (partitionId2 > partitionId1) {
                    List<RaftNode> partition2 = partitions.get(partitionId2);
                    
                    double minDistance = Double.MAX_VALUE;
                    RaftNode closestNode1 = null;
                    RaftNode closestNode2 = null;
                    
                    for (RaftNode node1 : partition1) {
                        Circle circle1 = (Circle) node1.getVisualNode().getChildren().get(0);
                        double x1 = circle1.getCenterX() + circle1.getTranslateX();
                        double y1 = circle1.getCenterY() + circle1.getTranslateY();
                        
                        for (RaftNode node2 : partition2) {
                            Circle circle2 = (Circle) node2.getVisualNode().getChildren().get(0);
                            double x2 = circle2.getCenterX() + circle2.getTranslateX();
                            double y2 = circle2.getCenterY() + circle2.getTranslateY();
                            
                            double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                            if (distance < minDistance) {
                                minDistance = distance;
                                closestNode1 = node1;
                                closestNode2 = node2;
                            }
                        }
                    }
                    
                    if (closestNode1 != null && closestNode2 != null) {
                        Circle circle1 = (Circle) closestNode1.getVisualNode().getChildren().get(0);
                        Circle circle2 = (Circle) closestNode2.getVisualNode().getChildren().get(0);
                        
                        javafx.scene.shape.Line partitionLine = new javafx.scene.shape.Line();
                        partitionLine.setStartX(circle1.getCenterX() + circle1.getTranslateX());
                        partitionLine.setStartY(circle1.getCenterY() + circle1.getTranslateY());
                        partitionLine.setEndX(circle2.getCenterX() + circle2.getTranslateX());
                        partitionLine.setEndY(circle2.getCenterY() + circle2.getTranslateY());
                        partitionLine.setStroke(Color.RED);
                        partitionLine.setStrokeWidth(2);
                        partitionLine.getStrokeDashArray().addAll(10.0, 5.0);
                        
                        visualizationPane.getChildren().add(partitionLine);
                        partitionLines.add(partitionLine);
                        
                        closestNode1.setPartitionLine(partitionLine);
                        closestNode2.setPartitionLine(partitionLine);
                    }
                }
            }
        }
    }
    
    public void healPartition() {
        for (javafx.scene.shape.Line line : partitionLines) {
            visualizationPane.getChildren().remove(line);
        }
        partitionLines.clear();
    
        int highestTerm = 0;
        RaftNode nodeWithHighestTerm = null;
        for (RaftNode node : nodes) {
            if (node.getCurrentTerm() > highestTerm) {
                highestTerm = node.getCurrentTerm();
                nodeWithHighestTerm = node;
            }
        }
    
        for (RaftNode node : nodes) {
            node.setPartitionId(0);
            node.setPartitionLine(null);
    
            if (node.getCurrentTerm() < highestTerm) {
                System.out.println("Node " + node.getId() + " updating term from " +
                    node.getCurrentTerm() + " to " + highestTerm + " after partition heal");
                node.stepDown(highestTerm);
            }
            node.setLastHeartbeat(System.currentTimeMillis());
        }
    
        if (nodeWithHighestTerm != null && nodeWithHighestTerm.getState().equals("LEADER")) {
            currentLeader = nodeWithHighestTerm;
        } else {
            currentLeader = null;
        }
    
        partitions.clear();
        drawConnectionLines();
    }
    
    public void stop() {
        timer.stop();
    }
    
    public List<RaftNode> getNodes() {
        return nodes;
    }
    
    public RaftNode getCurrentLeader() {
        return currentLeader;
    }
    

    public interface Message {
        Node getVisualNode();
    }
    
    public static class HeartbeatMessage implements Message {
        @Override
        public Node getVisualNode() {
            SVGPath heart = new SVGPath();
            heart.setContent("M10 30 A20 20 0 0 1 50 30 A20 20 0 0 1 90 30 Q90 60 50 90 Q10 60 10 30 Z");
            heart.setFill(Color.RED);
            heart.setScaleX(0.2);
            heart.setScaleY(0.2);
            return heart;
        }
    }
    
    public static class AppendEntriesMessage implements Message {
        private boolean success;
        
        public AppendEntriesMessage(boolean success) {
            this.success = success;
        }
        
        @Override
        public Node getVisualNode() {
            Circle messageCircle = new Circle(6);
            messageCircle.setFill(success ? Color.GREEN : Color.RED);
            return messageCircle;
        }
    }
    
    public static class RequestVoteMessage implements Message {
        @Override
        public Node getVisualNode() {
            Circle messageCircle = new Circle(6);
            messageCircle.setFill(Color.PURPLE);
            return messageCircle;
        }
    }
    
    public static class VoteMessage implements Message {
        @Override
        public Node getVisualNode() {
            Circle messageCircle = new Circle(6);
            messageCircle.setFill(Color.LIMEGREEN);
            return messageCircle;
        }
    }
    
    public static class MessageFactory {
        public static Message createMessage(String messageType, boolean success) {
            switch(messageType) {
                case "Heartbeat":
                    return new HeartbeatMessage();
                case "AppendEntries":
                    return new AppendEntriesMessage(success);
                case "RequestVote":
                    return new RequestVoteMessage();
                case "Vote":
                    return new VoteMessage();
                default:
                    return new AppendEntriesMessage(success);
            }
        }
    }
}
