package com.spacecay.expenselog.util

/** Tiny calculator for the amount keypad: + − × ÷ with normal precedence. */
object Calc {
    val ops = setOf('+', '−', '×', '÷')

    fun hasOp(expr: String) = expr.drop(1).any { it in ops }

    fun eval(expr: String): Double? {
        if (expr.isBlank()) return null
        val tokens = mutableListOf<String>()
        val cur = StringBuilder()
        expr.forEachIndexed { i, ch ->
            if (ch in ops && !(i == 0 && ch == '−')) {
                if (cur.isNotEmpty()) tokens += cur.toString()
                tokens += ch.toString()
                cur.clear()
            } else cur.append(if (ch == '−') '-' else ch)
        }
        if (cur.isNotEmpty()) tokens += cur.toString()
        // drop trailing operator
        while (tokens.isNotEmpty() && tokens.last().length == 1 && tokens.last()[0] in ops) tokens.removeAt(tokens.size - 1)
        if (tokens.isEmpty()) return null
        return try {
            // first pass × ÷
            val stack = mutableListOf<Any>()
            var i = 0
            stack += tokens[0].toDouble()
            i = 1
            while (i < tokens.size) {
                val op = tokens[i][0]
                val v = tokens[i + 1].toDouble()
                when (op) {
                    '×' -> stack[stack.size - 1] = (stack.last() as Double) * v
                    '÷' -> stack[stack.size - 1] = if (v == 0.0) return null else (stack.last() as Double) / v
                    else -> { stack += op; stack += v }
                }
                i += 2
            }
            var result = stack[0] as Double
            var j = 1
            while (j < stack.size) {
                val op = stack[j] as Char
                val v = stack[j + 1] as Double
                result = if (op == '+') result + v else result - v
                j += 2
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    /** Apply a keypad key to an expression. */
    fun press(expr: String, key: String): String {
        return when (key) {
            "⌫" -> expr.dropLast(1)
            "C" -> ""
            "." -> {
                val lastNum = expr.takeLastWhile { it !in ops }
                when {
                    lastNum.contains('.') -> expr
                    lastNum.isEmpty() -> expr + "0."
                    else -> "$expr."
                }
            }
            "+", "−", "×", "÷" -> {
                if (expr.isEmpty()) expr
                else if (expr.last() in ops) expr.dropLast(1) + key
                else expr + key
            }
            else -> {
                val lastNum = expr.takeLastWhile { it !in ops }
                // limit to 2 decimals
                if (lastNum.contains('.') && lastNum.substringAfter('.').length >= 2) expr
                else if (lastNum == "0") expr.dropLast(1) + key
                else if (lastNum.replace(".", "").length >= 12) expr
                else expr + key
            }
        }
    }

    /** Pretty-print the expression with grouping on each number. */
    fun pretty(expr: String): String {
        if (expr.isEmpty()) return "0"
        val sb = StringBuilder()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isEmpty()) return
            val s = cur.toString()
            val intPart = s.substringBefore('.')
            val dec = if (s.contains('.')) "." + s.substringAfter('.') else ""
            val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
            sb.append(grouped.ifEmpty { "0" }).append(dec)
            cur.clear()
        }
        expr.forEach { ch -> if (ch in ops) { flush(); sb.append(' ').append(ch).append(' ') } else cur.append(ch) }
        flush()
        return sb.toString()
    }
}
