import AST.Token;
import java.util.LinkedList;
import java.util.Optional;

public class TokenManager {

    private final LinkedList<Token> tokens;

    public TokenManager(LinkedList<Token> tokens) {
        this.tokens = tokens;
    }

    // Returns the line number of the current token.
    public int getCurrentLine() {
        if (tokens.isEmpty()) {
            throw new IllegalStateException("No tokens available to get line number");
        }
        return tokens.peek().LineNumber;
    }

    // Returns the column number of the current token.
    public int getCurrentColumnNumber() {
        if (tokens.isEmpty()) {
            throw new IllegalStateException("No tokens available to get column number");
        }
        return tokens.peek().ColumnNumber;
    }

    // Returns true if all tokens have been consumed.
    public boolean Done() {
        return tokens.isEmpty();
    }

    // If the next token matches the expected type, remove and return it.
    public Optional<Token> MatchAndRemove(Token.TokenTypes t) {
        if (tokens.isEmpty()) return Optional.empty();
        Token next = tokens.peek();
        if (next.Type == t) return Optional.of(tokens.remove());
        return Optional.empty();
    }

    public Optional<Token> Peek(int i) {
        if (i < tokens.size()) return Optional.of(tokens.get(i));
        return Optional.empty();
    }
}