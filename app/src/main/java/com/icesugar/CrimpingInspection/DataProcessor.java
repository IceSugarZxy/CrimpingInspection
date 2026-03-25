package com.icesugar.CrimpingInspection;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import com.icesugar.CrimpingInspection.util.TypeConversion;

public class DataProcessor {
    // 使用 StringBuilder 来存储接收结果
    // 注意：静态 StringBuilder 在多线程环境下不安全，这里使用同步块保护
    private static final StringBuilder receiveResult = new StringBuilder();
    // 设置最大缓冲区大小，防止内存溢出（例如：100KB）
    private static final int MAX_BUFFER_SIZE = 100 * 1024; // 100KB
    // 同步锁对象
    private static final Object LOCK = new Object();

    /**
     * 处理原始数据（数据模式使用）- 不做分包处理，十六进制转ASCII字符串后返回
     * @param recBufSuc 接收的字节数组
     * @return ASCII字符串格式的原始数据
     */
    public static String processRawData(byte[] recBufSuc) {
        if (recBufSuc == null || recBufSuc.length == 0) {
            return null;
        }
        // 十六进制转ASCII字符串
        return TypeConversion.asciiToString(TypeConversion.bytes2HexString(recBufSuc));
    }

    // 处理接收到的数据（波形模式使用）
    public static ProcessingResult processData(byte[] recBufSuc) {
        // 检查输入数据是否为空
        if (recBufSuc == null || recBufSuc.length == 0) {
            return null;
        }

        synchronized (LOCK) {
            // 检查缓冲区是否已满，如果已满则清空缓冲区并记录警告
            if (receiveResult.length() >= MAX_BUFFER_SIZE) {
                Log.w("DataProcessor", "缓冲区已满，清空缓冲区以防止内存溢出。当前大小: " + receiveResult.length());
                receiveResult.setLength(0); // 清空缓冲区
            }

            // 将新接收到的数据追加到 receiveResult 中
            String newData = TypeConversion.asciiToString(TypeConversion.bytes2HexString(recBufSuc));
            receiveResult.append(newData);
            Log.i("receiveResult", "receiveResult: " + receiveResult + " (长度: " + receiveResult.length() + ")");

            // 检查输入字符串中是否包含 "\n"
            if (receiveResult.toString().contains("\n")) {
                // 提取换行符前的部分
                String completePart = receiveResult.substring(0, receiveResult.indexOf("\n"));
                Log.i("processData", "completePart: " + completePart);
                // 从 receiveResult 中删除提取的部分
                receiveResult.delete(0, receiveResult.indexOf("\n") + 1); // 删除换行符前的部分
                return parseCompletePart(completePart);  // 解析并返回结果
            } else {
                return null;
            }
        }
    }

    private static ProcessingResult parseCompletePart(String completePart) {
        try {
            // 检查字符串长度（固定19位：5 + 5 + 9）
            if (completePart.length() != 19) {
                return null;
            }

            // 提取各段数据
            String permAStr = completePart.substring(0, 5);
            String permBStr = completePart.substring(5, 10);
            String eddyStr = completePart.substring(10, 19);

            // perm A/B：5位，可能带正负号
            if (!isValidNumericString(permAStr) || !isValidNumericString(permBStr)) {
                Log.w("DataProcessor", "perm数据格式无效: permA=" + permAStr + ", permB=" + permBStr);
                return null;
            }
            // eddy：9位，纯数字无符号
            if (!isPureDigits(eddyStr)) {
                Log.w("DataProcessor", "eddy数据格式无效: eddy=" + eddyStr);
                return null;
            }

            float permeability_A = Float.parseFloat(permAStr);
            float permeability_B = Float.parseFloat(permBStr);
            float eddy = Float.parseFloat(eddyStr);

            // 校验数值
            if (Float.isNaN(permeability_A) || Float.isNaN(permeability_B) || Float.isNaN(eddy) ||
                Float.isInfinite(permeability_A) || Float.isInfinite(permeability_B) || Float.isInfinite(eddy)) {
                Log.w("DataProcessor", "数据包含NaN或Infinity");
                return null;
            }

            float permeability = (float) Math.sqrt(Math.pow(permeability_A, 2) + Math.pow(permeability_B, 2));
            // 避免除零
            float arct = (float) Math.atan(permeability_A / (permeability_B + 1e-6f));
            float permeability_arct_degrees = (float) Math.toDegrees(arct);

            List<Float> ValueAndPhase_list = new ArrayList<>();
            ValueAndPhase_list.add(permeability);
            ValueAndPhase_list.add(permeability_arct_degrees);

            String add_putout = eddy + "," + permeability + "," + permeability_arct_degrees + "\n";
            Log.i("RECEIVE_SUCCESS:", "\n" + add_putout);

            return new ProcessingResult(eddy, ValueAndPhase_list, add_putout);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            Log.w("DataProcessor", "解析数据包失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 校验字符串是否为合法的数值格式（可选的正负号开头，后跟数字）
     */
    private static boolean isValidNumericString(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        int start = 0;
        // 检查首位是否为符号
        if (str.charAt(0) == '+' || str.charAt(0) == '-') {
            start = 1;
        }
        // 剩余部分必须全是数字，且至少有一位
        if (start >= str.length()) {
            return false;  // 只有符号没有数字
        }
        for (int i = start; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验字符串是否为纯数字（无符号）
     */
    private static boolean isPureDigits(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // 处理结果类
    public static class ProcessingResult {
        public float eddy;
        public List<Float> Perm_list;
        public String add_putout;

        public ProcessingResult(float eddy, List<Float> permeabilityList, String add_putout) {
            this.eddy = eddy;
            this.Perm_list = permeabilityList;
            this.add_putout = add_putout;
        }
    }
}