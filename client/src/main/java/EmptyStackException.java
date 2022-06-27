class EmptyStackException extends RuntimeException {
    EmptyStackException() { throw new RuntimeException("Stack is empty"); }
}
