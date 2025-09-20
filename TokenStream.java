import java.util.*;

final class TokenStream {
    private final List<Token> tokens; // the entire token list produced by the lexer (must end with T_EOF)
    private int i = 0; // current index into tokens, where we are up to

    TokenStream(List<Token> tokens) {
        this.tokens = Objects.requireNonNull(tokens); // immediate exception if null
        if(tokens.isEmpty() || tokens.get(tokens.size() - 1).tokenType != TokenType.T_EOF) { // immediate exception if T_EOF isnt in the list
            throw new IllegalArgumentException("Token list must end with T_EOF");
        }
    }

    // look at current token without consuming
    Token peek() {
        return tokens.get(i);
    }

    // lookahead k tokens
    Token lookahead(int k) {
        int j = i + Math.max(0, k); // done in case k is negative, if it is just put it to 0, we dont want to accidentally move backwards
        if (j >= tokens.size()) { // to make sure j doesnt run past end of the list
            j = tokens.size() - 1; // if we did go beyond it, clamp j to the last valid token in the list (assumedly this would be T_EOF)
        }
        return tokens.get(j); // return the token
    }

    // attempts to consume the current token iff it matches the expected tt, used when a token is optional
    boolean match(TokenType tt) {
        if(peek().tokenType == tt) { // inspects the current token
            consume(); // consume the token
            return true; // matched and consumed
        }
        return false; // did not match and did not consume
    }

    // used when a token is mandatory 
    Token expect(TokenType tt) {
        Token p = peek(); // grab current token
        if(p.tokenType != tt) { // compare current token against what we require
            return null; // doesn't match, we dont advance, return null
        }
        consume(); // if it did match, consume it
        return p; // return the token
    }

    // consumes the current token
    void consume() {
        i++;
    }

    Token previous() {
        int j = i - 1;
        if (j < 0) j = 0;                    // before start → clamp
        if (j >= tokens.size()) j = tokens.size() - 1; // past end → clamp
        return tokens.get(j);
    }

    int mark() { return i; }

    void reset(int pos) {
        // clamp to [0, tokens.size()-1]
        if (pos < 0) pos = 0;
        if (pos >= tokens.size()) pos = tokens.size() - 1;
        i = pos;
    }

    /**
     * Used to skip over until we find a new safe point
     * e.g. x = 5 y = 10;
     * expect(TSEMI) fails after 5 cus of no ;
     * report the error and then call syncTo, so we can skip over the y token until we find the next safe point ; and parsing resumes at that safe point
     * if no syncTo, everything after x would just be a cascade of false errors
     * @param follow
     */
    void syncTo(Set<TokenType> follow) { // follow is a set of token types that are considered safe sync points e.g. semicolons, end, else etc.
        while(i < tokens.size() && !follow.contains(peek().tokenType)) { // dont go past the end of the token list and while the current token is not one of the "safe" ones
            i++; // skip the current token
        }
    }

    void syncToEither(Set<TokenType> follow, Set<TokenType> starts) {
        while (i < tokens.size()) {
            TokenType t = peek().tokenType;
            if (follow.contains(t) || starts.contains(t)) break;
            i++;
        }
    }
}
