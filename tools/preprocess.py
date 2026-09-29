"""Preprocessor for multi-version Fabric mod sources.

Marker syntax (line comments in .java files):

    //? if <expr>          start conditional block
    //? elif <expr>        else-if branch
    //? else               else branch
    //? endif              end block
    //? file-if <expr>     if first line of file and expr is false -> drop whole file

Expressions: flag names, !flag, &&, || (evaluated against the version's flag dict).
Inactive branches are removed; active branches keep their own indentation.
"""

import re

MARKER = re.compile(r"^(\s*)//\?\s*(file-if|if|elif|else|endif)\b\s*(.*)$")


def eval_expr(expr, flags):
    expr = expr.strip()
    if not expr:
        raise ValueError("empty condition")
    tokens = re.findall(r"\s*([()]|&&|\|\||!|[A-Za-z_][A-Za-z0-9_]*)", expr)
    # validate & translate to python
    out = []
    for t in tokens:
        if t in ("&&", "||", "!", "(", ")"):
            out.append({"&&": "and", "||": "or", "!": "not"}.get(t, t))
        else:
            if t not in flags:
                raise ValueError(f"unknown flag: {t}")
            out.append("True" if flags[t] else "False")
    py = " ".join(out)
    return bool(eval(py, {"__builtins__": {}}, {}))  # noqa: S307 - controlled input


def preprocess_text(text, flags, path=""):
    lines = text.splitlines()
    # file-level drop
    if lines:
        m = MARKER.match(lines[0])
        if m and m.group(2) == "file-if":
            if not eval_expr(m.group(3), flags):
                return None
            lines = lines[1:]

    out = []
    stack = []  # dicts: parent, taken, active
    for i, line in enumerate(lines, 1):
        m = MARKER.match(line)
        if m:
            kind, expr = m.group(2), m.group(3)
            if kind == "file-if":
                raise ValueError(f"{path}:{i}: file-if must be the first line")
            if kind == "if":
                parent = stack[-1]["active"] if stack else True
                cond = eval_expr(expr, flags) if parent else False
                if not parent:
                    # short-circuit: parse but never activate
                    eval_expr(expr, flags)
                stack.append({"parent": parent, "taken": cond, "active": parent and cond})
            elif kind == "elif":
                if not stack:
                    raise ValueError(f"{path}:{i}: elif without if")
                f = stack[-1]
                cond = eval_expr(expr, flags) if f["parent"] and not f["taken"] else False
                f["active"] = f["parent"] and not f["taken"] and cond
                f["taken"] = f["taken"] or cond
            elif kind == "else":
                if not stack:
                    raise ValueError(f"{path}:{i}: else without if")
                f = stack[-1]
                f["active"] = f["parent"] and not f["taken"]
                f["taken"] = True
            elif kind == "endif":
                if not stack:
                    raise ValueError(f"{path}:{i}: endif without if")
                stack.pop()
            continue
        if not stack or stack[-1]["active"]:
            out.append(line)
    if stack:
        raise ValueError(f"{path}: unterminated //? block ({len(stack)} open)")
    return "\n".join(out) + ("\n" if text.endswith("\n") else "")


def preprocess_file(src, dst, flags):
    with open(src, "r", encoding="utf-8") as f:
        text = f.read()
    res = preprocess_text(text, flags, path=str(src))
    if res is None:
        if os.path.exists(dst):
            os.remove(dst)
        return False
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, "w", encoding="utf-8", newline="\n") as f:
        f.write(res)
    return True


import os  # noqa: E402  (used by preprocess_file)
