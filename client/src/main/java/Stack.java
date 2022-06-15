public class Stack {
    private int maxSize; // размер
    private String[] stack; // место хранения
    private int head;    // вершина

    public Stack(int size) {
        maxSize = size;
        stack = new String[maxSize];
        head = -1;
    }

    public boolean isEmpty() {
        return head == -1;
    }

    public boolean isFull() {
        return head == maxSize - 1;
    }

    public void clear() { head = -1; }

    public void push(String val) {
        if (isFull()) {
            maxSize *= 2;
            String[] newStack = new String[maxSize];
            System.arraycopy(stack, 0, newStack, 0, stack.length);
            stack = newStack;
        }
        stack[++head] = val;
    }

    public String pop() {
        if (isEmpty()) throw new RuntimeException("Stack is empty"); //ret -1
        return stack[head--];
    }

    public String peek() {
        return stack[head];
    }
}