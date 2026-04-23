import AST.*;
import java.util.Optional;
import java.util.LinkedList;

public class NushaFall2025Parser {
    private TokenManager tm;

    //Empty constructor
    public NushaFall2025Parser() {}

    //Construxts the AST from a list of tokens.
    public Optional<Nusha> Nusha(LinkedList<Token> tokens) throws SyntaxErrorException {
        tm = new TokenManager(tokens);
        Nusha result = new Nusha();

        result.definitions = new Definitions();
        result.definitions.definition = parseDefinitions();

        result.variables = new Variables();
        result.variables.variable = parseVariables();

        result.rules = new Rules();
        result.rules.rule = parseRules();

        return Optional.of(result);
    }


    private LinkedList<Definition> parseDefinitions() throws SyntaxErrorException {
        LinkedList<Definition> defs = new LinkedList<>();

        while (!tm.Done()) {
            while (tm.MatchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {}
            Optional<Definition> def = parseDefinition();
            if (def.isPresent()) defs.add(def.get());
            else break;
        }

        return defs;
    }
    //single definition either a choice list {} or a struct []
    private Optional<Definition> parseDefinition() throws SyntaxErrorException {
        Optional<Token> name = tm.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if (!name.isPresent()) return Optional.empty();
        if (!tm.MatchAndRemove(Token.TokenTypes.EQUAL).isPresent()) return Optional.empty();

        if (tm.MatchAndRemove(Token.TokenTypes.LEFTCURLY).isPresent()) {
            Definition def = new Definition();
            def.definitionName = name.get().Value.orElse("");
            def.choices = Optional.of(new Choices());
            def.choices.get().choice = new LinkedList<>();

            do {
                Optional<Token> id = tm.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
                if (!id.isPresent())
                    throw new SyntaxErrorException("Expected identifier inside choices", tm.getCurrentLine(), tm.getCurrentColumnNumber());
                def.choices.get().choice.add(id.get().Value.orElse(""));
            } while (tm.MatchAndRemove(Token.TokenTypes.COMMA).isPresent());

            if (!tm.MatchAndRemove(Token.TokenTypes.RIGHTCURLY).isPresent())
                throw new SyntaxErrorException("Expected '}' after choices", tm.getCurrentLine(), tm.getCurrentColumnNumber());

            RequireNewLine();
            def.nstruct = Optional.empty();
            return Optional.of(def);
        }

        if (tm.MatchAndRemove(Token.TokenTypes.LEFTBRACE).isPresent()) {
            Definition def = new Definition();
            def.definitionName = name.get().Value.orElse("");
            def.nstruct = Optional.of(new NStruct());
            def.nstruct.get().entry = new LinkedList<>();

            do {
                def.nstruct.get().entry.add(parseEntry());
            } while (tm.MatchAndRemove(Token.TokenTypes.COMMA).isPresent());

            if (!tm.MatchAndRemove(Token.TokenTypes.RIGHTBRACE).isPresent())
                throw new SyntaxErrorException("Expected ']' after struct", tm.getCurrentLine(), tm.getCurrentColumnNumber());

            RequireNewLine();
            def.choices = Optional.empty();
            return Optional.of(def);
        }

        return Optional.empty();
    }
    //single struct entry
    private Entry parseEntry() throws SyntaxErrorException {
        Entry e = new Entry();
        e.unique = tm.MatchAndRemove(Token.TokenTypes.UNIQUE).isPresent();

        Optional<Token> type = tm.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        Optional<Token> name = tm.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if (!type.isPresent() || !name.isPresent())
            throw new SyntaxErrorException("Expected type and field name", tm.getCurrentLine(), tm.getCurrentColumnNumber());

        e.type = type.get().Value.orElse("");
        e.name = name.get().Value.orElse("");
        return e;
    }
    //Variables = Variable*
    private LinkedList<Variable> parseVariables() throws SyntaxErrorException {
        LinkedList<Variable> vars = new LinkedList<>();

        while (!tm.Done()) {
            while (tm.MatchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {}
            Optional<Variable> v = parseVariable();
            if (v.isPresent()) vars.add(v.get());
            else break;
        }

        return vars;
    }
    //Parses single variable declaration
    private Optional<Variable> parseVariable() throws SyntaxErrorException {
        if (!tm.MatchAndRemove(Token.TokenTypes.VAR).isPresent()) return Optional.empty();

        Optional<Token> name = tm.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if (!name.isPresent())
            throw new SyntaxErrorException("Expected variable name", tm.getCurrentLine(), tm.getCurrentColumnNumber());

        if (!tm.MatchAndRemove(Token.TokenTypes.COLON).isPresent())
            throw new SyntaxErrorException("Expected ':' after var name", tm.getCurrentLine(), tm.getCurrentColumnNumber());

        Optional<Token> type = tm.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if (!type.isPresent())
            throw new SyntaxErrorException("Expected type after ':'", tm.getCurrentLine(), tm.getCurrentColumnNumber());

        Optional<Token> open = tm.MatchAndRemove(Token.TokenTypes.LEFTBRACE);
        Optional<Token> size = Optional.empty();
        if (open.isPresent()) {
            size = tm.MatchAndRemove(Token.TokenTypes.NUMBER);
            if (!size.isPresent())
                throw new SyntaxErrorException("Expected number inside brackets", tm.getCurrentLine(), tm.getCurrentColumnNumber());
            if (!tm.MatchAndRemove(Token.TokenTypes.RIGHTBRACE).isPresent())
                throw new SyntaxErrorException("Expected closing ']' after size", tm.getCurrentLine(), tm.getCurrentColumnNumber());
        }

        RequireNewLine();

        Variable v = new Variable();
        v.variableName = name.get().Value.orElse("");
        v.type = type.get().Value.orElse("");
        v.size = size.map(t -> t.Value.orElse(""));

        return Optional.of(v);
    }
    //Parses a sequence of rules
    private LinkedList<Rule> parseRules() throws SyntaxErrorException {
        LinkedList<Rule> rules = new LinkedList<>();
        while (!tm.Done()) {
            while (tm.MatchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) {}
            Optional<Rule> r = parseRule();
            if (r.isPresent()) rules.add(r.get());
            else break;
        }
        return rules;
    }

    private Optional<Rule> parseRule() throws SyntaxErrorException {
        Optional<Expression> cond = parseExpression();
        if (!cond.isPresent()) return Optional.empty();

        Rule rule = new Rule();
        rule.expression = cond.get();
        rule.thens = new LinkedList<>();

        if (tm.MatchAndRemove(Token.TokenTypes.YIELDS).isPresent()) {
            RequireNewLine();
            if (tm.MatchAndRemove(Token.TokenTypes.INDENT).isPresent()) {
                while (!tm.MatchAndRemove(Token.TokenTypes.DEDENT).isPresent()) {
                    Optional<Expression> e = parseExpression();
                    if (e.isEmpty())
                        throw new SyntaxErrorException("Expected expression inside rule block", tm.getCurrentLine(), tm.getCurrentColumnNumber());
                    rule.thens.add(e.get());
                    RequireNewLine();
                }
            }
        }
        return Optional.of(rule);
    }
    //Parses an expression
    private Optional<Expression> parseExpression() throws SyntaxErrorException {
        Optional<VariableReference> left = parseVariableReference();
        if (left.isEmpty()) return Optional.empty();

        Optional<Token> opTok = tm.MatchAndRemove(Token.TokenTypes.EQUAL);
        if (!opTok.isPresent()) opTok = tm.MatchAndRemove(Token.TokenTypes.NOTEQUAL);
        if (opTok.isEmpty()) return Optional.empty();

        Optional<VariableReference> right = parseVariableReference();
        if (right.isEmpty())
            throw new SyntaxErrorException("Expected variable after operator", tm.getCurrentLine(), tm.getCurrentColumnNumber());

        Expression e = new Expression();
        e.left = left.get();
        e.right = right.get();
        e.op = new Op();
        e.op.type = (opTok.get().Type == Token.TokenTypes.EQUAL) ? Op.OpTypes.Equal : Op.OpTypes.NotEqual;

        return Optional.of(e);
    }
    //Parses variable references
    private Optional<VariableReference> parseVariableReference() throws SyntaxErrorException {
        Optional<Token> name = tm.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if (name.isEmpty()) return Optional.empty();

        VariableReference ref = new VariableReference();
        ref.variableName = name.get().Value.orElse("");
        ref.vrmodifier = parseVRModifier();
        return Optional.of(ref);
    }

    private Optional<VRModifier> parseVRModifier() throws SyntaxErrorException {
        Optional<VRModifier> mod = Optional.empty();

        if (tm.MatchAndRemove(Token.TokenTypes.DOT).isPresent()) {
            Optional<Token> id = tm.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
            if (id.isEmpty())
                throw new SyntaxErrorException("Expected identifier after '.'", tm.getCurrentLine(), tm.getCurrentColumnNumber());
            VRModifier m = new VRModifier();
            m.dot = true;
            m.part = Optional.of(id.get().Value.orElse(""));
            m.vrmodifier = parseVRModifier();
            mod = Optional.of(m);
        } else if (tm.MatchAndRemove(Token.TokenTypes.LEFTBRACE).isPresent()) {
            Optional<Token> num = tm.MatchAndRemove(Token.TokenTypes.NUMBER);
            if (num.isEmpty())
                throw new SyntaxErrorException("Expected number inside '[' ']'", tm.getCurrentLine(), tm.getCurrentColumnNumber());
            if (!tm.MatchAndRemove(Token.TokenTypes.RIGHTBRACE).isPresent())
                throw new SyntaxErrorException("Expected closing ']'", tm.getCurrentLine(), tm.getCurrentColumnNumber());
            VRModifier m = new VRModifier();
            m.size = num.get().Value.orElse("");
            m.vrmodifier = parseVRModifier();
            mod = Optional.of(m);
        }

        return mod;
    }
    //Helper method to ensure newline exists after a construct
    private void RequireNewLine() throws SyntaxErrorException {
        boolean found = false;
        while (tm.MatchAndRemove(Token.TokenTypes.NEWLINE).isPresent()) found = true;
        if (!found)
            throw new SyntaxErrorException("Expected newline", tm.getCurrentLine(), tm.getCurrentColumnNumber());
    }
}