package example.com.example;

public class ChatMessage{
    private String username;
    private String content;

    // Getters and setters are a must for Jackson to serialize/deserialize objects
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "@" + username + ":" + content;
    }
}