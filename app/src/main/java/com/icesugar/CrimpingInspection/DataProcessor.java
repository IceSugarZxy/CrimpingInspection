package com.icesugar.CrimpingInspection;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import com.icesugar.CrimpingInspection.util.TypeConversion;

public class DataProcessor {
    // 使用 StringBuilder 来存储接收结果
    private static StringBuilder receiveResult = new StringBuilder();
    // 设置最大缓冲区大小，防止内存溢出（例如：100KB）
    private static final int MAX_BUFFER_SIZE = 100 * 1024; // 100KB

    // 处理接收到的数据
    public static ProcessingResult processData(byte[] recBufSuc) {
        // 检查输入数据是否为空
        if (recBufSuc == null || recBufSuc.length == 0) {
            return null;
        }

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

    private static ProcessingResult parseCompletePart(String completePart) {
        try {
            // 检查字符串长度
            float permeability_A;
            float permeability_B;
            float eddy;
            if (completePart.length() >= 19) { // 5 + 5 + 9 = 19
                // 根据指定的长度进行切分
                permeability_A = Float.parseFloat(completePart.substring(0, 5));
                permeability_B = Float.parseFloat(completePart.substring(5, 10));
                eddy = Float.parseFloat(completePart.substring(10, 19));
            } else {
                return null;
            }

            float permeability = (float) Math.sqrt(Math.pow(permeability_A, 2) + Math.pow(permeability_B, 2));
            float permeability_arct = (float) Math.atan(permeability_A / permeability_B);
            float permeability_arct_degrees = (float) Math.toDegrees(permeability_arct);

            List<Float> ValueAndPhase_list = new ArrayList<>();
            ValueAndPhase_list.add(permeability);
            ValueAndPhase_list.add(permeability_arct_degrees);

            String add_putout = eddy + "," + permeability + "," + permeability_arct_degrees + "\n";
            Log.i("RECEIVE_SUCCESS:", "\n" + add_putout);

            return new ProcessingResult(eddy, ValueAndPhase_list, add_putout);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return null;  // 或者处理错误情况
        }
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