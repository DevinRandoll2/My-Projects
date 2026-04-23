import AST.*;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

public class Lexer {
    private final TextManager textManager;
    private final Map<String, Token.TokenTypes> Keywords = new HashMap<>();
    private final Map<String, Token.TokenTypes> Punctuations = new HashMap<>();
    private final LinkedList<Token> tokens = new LinkedList<>();
    private int currentIndent = 0;

    public Lexer(String input) {
        this.textManager = new TextManager(input);

        //Keywords from public enum TokenTypes
        Keywords.put("var", Token.TokenTypes.VAR);
        Keywords.put("unique", Token.TokenTypes.UNIQUE);

        //Punctuation from public enum TokenTypes
        Punctuations.put("=", Token.TokenTypes.EQUAL);
        Punctuations.put("{", Token.TokenTypes.LEFTCURLY);
        Punctuations.put("}", Token.TokenTypes.RIGHTCURLY);
        Punctuations.put(",", Token.TokenTypes.COMMA);
        Punctuations.put(":", Token.TokenTypes.COLON);
        Punctuations.put("[", Token.TokenTypes.LEFTBRACE);
        Punctuations.put("]", Token.TokenTypes.RIGHTBRACE);
        Punctuations.put(".", Token.TokenTypes.DOT);
    }

    public LinkedList<Token> Lex() throws SyntaxErrorException {
        while (!textManager.isAtEnd()) {
            char c = textManager.PeekCharacter();

            if (textManager.getColumn() == 1) {
                handleIndentation();
                if (textManager.isAtEnd()) break;
                c = textManager.PeekCharacter();
            }

            if (c == ' ' || c == '\t') {
                textManager.GetCharacter();
                continue;
            }

            if (c == '\n') {
                textManager.GetCharacter();
                tokens.add(new Token(Token.TokenTypes.NEWLINE,
                        textManager.getLine(), textManager.getColumn()));
                continue;
            }
            //Hanldes Words
            if (Character.isLetter(c)) {
                tokens.add(readWord());
                continue;
            }
            //Handles Number
            if (Character.isDigit(c)) {
                tokens.add(readNumber());
                continue;
            }

            if (c == '!' && textManager.PeekNext() == '=') {
                int line = textManager.getLine();
                int column = textManager.getColumn();
                textManager.GetCharacter();
                textManager.GetCharacter();
                tokens.add(new Token(Token.TokenTypes.NOTEQUAL, line, column));
                continue;
            }

            if (c == '=' && textManager.PeekNext() == '>') {
                int line = textManager.getLine();
                int column = textManager.getColumn();
                textManager.GetCharacter();
                textManager.GetCharacter();
                tokens.add(new Token(Token.TokenTypes.YIELDS, line, column));
                continue;
            }

            if (Punctuations.containsKey(Character.toString(c))) {
                int line = textManager.getLine();
                int column = textManager.getColumn();
                String p = Character.toString(textManager.GetCharacter());
                tokens.add(new Token(Punctuations.get(p), line, column));
                continue;
            }

            throw new SyntaxErrorException(
                    "Unexpected character: '" + c + "'",
                    textManager.getLine(),
                    textManager.getColumn()
            );
        }

        while (currentIndent > 0) {
            tokens.add(new Token(Token.TokenTypes.DEDENT,
                    textManager.getLine(), textManager.getColumn()));
            currentIndent--;
        }

        tokens.add(new Token(Token.TokenTypes.NEWLINE,
                textManager.getLine(), textManager.getColumn()));

        return tokens;
    }

    public Token readWord() {
        int line = textManager.getLine();
        int column = textManager.getColumn();
        StringBuilder buffer = new StringBuilder();

        while (!textManager.isAtEnd() &&
                Character.isLetterOrDigit(textManager.PeekCharacter())) {
            buffer.append(textManager.GetCharacter());
        }

        String word = buffer.toString();
        if (Keywords.containsKey(word)) {
            return new Token(Keywords.get(word), line, column);
        } else {
            return new Token(Token.TokenTypes.IDENTIFIER, line, column, word);
        }
    }

    public Token readNumber() {
        int line = textManager.getLine();
        int column = textManager.getColumn();
        StringBuilder buffer = new StringBuilder();

        while (!textManager.isAtEnd() && Character.isDigit(textManager.PeekCharacter())) {
            buffer.append(textManager.GetCharacter());
        }
        return new Token(Token.TokenTypes.NUMBER, line, column, buffer.toString());
    }

    private void handleIndentation() throws SyntaxErrorException {
        if (textManager.isAtEnd()) return;

        int count = 0;
        int line = textManager.getLine();
        int column = textManager.getColumn();

        while (!textManager.isAtEnd()) {
            char c = textManager.PeekCharacter();
            if (c == ' ') {
                textManager.GetCharacter();
                count++;
            } else if (c == '\t') {
                textManager.GetCharacter();
                count += 4;
            } else {
                break;
            }
        }

        if (count % 4 != 0) {
            throw new SyntaxErrorException(
                    "Indentation not multiple of 4",
                    textManager.getLine(),
                    textManager.getColumn()
            );
        }

        int indentLevel = count / 4;
        while (indentLevel > currentIndent) {
            tokens.add(new Token(Token.TokenTypes.INDENT,
                    textManager.getLine(), textManager.getColumn()));
            currentIndent++;
        }
        while (indentLevel < currentIndent) {
            tokens.add(new Token(Token.TokenTypes.DEDENT,
                    textManager.getLine(), textManager.getColumn()));
            currentIndent--;
        }
    }
}