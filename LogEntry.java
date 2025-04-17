public class LogEntry {
    private int term;
    private int index;
    private String command;
    
    public LogEntry(int term, int index, String command) {
        this.term = term;
        this.index = index;
        this.command = command;
    }
    
    public int getTerm() {
        return term;
    }
    
    public int getIndex() {
        return index;
    }
    
    public String getCommand() {
        return command;
    }
    
    @Override
    public String toString() {
        return String.format("[%d] Term: %d, Command: %s", index, term, command);
    }
} 