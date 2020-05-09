public class PreExecutor {
    private String code;
    private int index = 0;
    private StringBuilder output = new StringBuilder();
    private char[] cells = new char[10000];
    private int ptr = 0;

    public PreExecutor(String code) {
        this.code = code;
    }

    public void execute() {
        for (; index < code.length(); index++) {
            if (code.charAt(index) == '+') {
                cells[ptr]++;
            } else if (code.charAt(index) == '-') {
                cells[ptr]--;
            } else if (code.charAt(index) == '>') {
                ptr++;
            } else if (code.charAt(index) == '<') {
                ptr--;
            } else if (code.charAt(index) == '.') {
                output.append(cells[ptr]);
            } else if (code.charAt(index) == ',') {
                return;
            } else if (code.charAt(index) == '[') {
                if (index + 2 < code.length() && (code.charAt(index+1) == '+' || code.charAt(index+1) == '-') && code.charAt(index+2) == ']') {
                    cells[ptr] = 0;
                    index += 2;
                } else {
                    int beginIndex = index;
                    int parenIndex = 1;
                    while (parenIndex != 0) {
                        index++;
                        if (code.charAt(index) == ']') {
                            parenIndex--;
                        } else if (code.charAt(index) == '[') {
                            parenIndex++;
                        }
                    }
                    String cycleCode = code.substring(beginIndex + 1, index);
                    if (cycleCode.indexOf(',') != -1) {
                        index = beginIndex;
                        return;
                    }
                    while (cells[ptr] != 0) {
                        index = beginIndex + 1;
                        execute();
                    }
                }
            } else if (code.charAt(index) == ']') {
                return;
            }
        }
    }

    public int getIndex() {
        return index;
    }

    public StringBuilder getOutput() {
        return output;
    }

    public String getCode() {
        return code;
    }

    public char[] getCells() {
        return cells;
    }

    public int getPtr() {
        return ptr;
    }
}
