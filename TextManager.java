public class TextManager {
    private final String text;
    private int position;
    private int line = 1;
    private int column = 1;

    public TextManager(String input) {
        this.text = input;
        this.position = 0;
    }

    public boolean isAtEnd() {
        return position >= text.length();
    }

    public char PeekCharacter() {
        if (position < text.length()) {
            return text.charAt(position);
        }

        return '\0';
    }

    public char PeekCharacter(int dist) {
        if (position + dist < text.length()) {
            return text.charAt(position + dist);
        }
        return '\0';
    }

    public char GetCharacter() {
        if (this.isAtEnd()) {
            return '\0';
        }
        char c = text.charAt(position);
        position++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public char PeekNext() {
        return  PeekCharacter(1);

    }

}
