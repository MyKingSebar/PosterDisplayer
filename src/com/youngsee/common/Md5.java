/*
 * Copyright (C) 2013 poster PCE YoungSee Inc. 
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Md5
{
    private Logger logger = new Logger();
    
    private int A = 0;
    private int B = 0;
    private int C = 0;
    private int D = 0;
    
    private static final int S11 = 7;
    private static final int S12 = 12;
    private static final int S13 = 17;
    private static final int S14 = 22;
    private static final int S21 = 5;
    private static final int S22 = 9;
    private static final int S23 = 14;
    private static final int S24 = 20;
    private static final int S31 = 4;
    private static final int S32 = 11;
    private static final int S33 = 16;
    private static final int S34 = 23;
    private static final int S41 = 6;
    private static final int S42 = 10;
    private static final int S43 = 15;
    private static final int S44 = 21;

    private final static String[] strHexDigits =
    { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };
    
    /*
     * F, G, H and I are basic MD5 functions. 四个非线性函数:
     * 
     * F(X,Y,Z) =(X&Y)|((~X)&Z) G(X,Y,Z) =(X&Z)|(Y&(~Z)) H(X,Y,Z) =X^Y^Z I(X,Y,Z)=Y^(X|(~Z))
     * 
     * （&与，|或，~非，^异或）
     */
    private int F(int x, int y, int z)
    {
        return (x & y) | ((~x) & z);
    }

    private int G(int x, int y, int z)
    {
        return (x & z) | (y & (~z));
    }

    private int H(int x, int y, int z)
    {
        return x ^ y ^ z;
    }

    private int I(int x, int y, int z)
    {
        return y ^ (x | (~z));
    }

    /*
     * FF, GG, HH, and II transformations for rounds 1, 2, 3, and 4. Rotation is separate from addition to prevent recomputation.
     */
    private int FF(int a, int b, int c, int d, int mj, int s, int ti)
    {
        a = a + F(b, c, d) + mj + ti;
        a = a << s | a >>> (32 - s);
        a += b;
        return a;
    }

    private int GG(int a, int b, int c, int d, int mj, int s, int ti)
    {
        a = a + G(b, c, d) + mj + ti;
        a = a << s | a >>> (32 - s);
        a += b;
        return a;
    }

    private int HH(int a, int b, int c, int d, int mj, int s, int ti)
    {
        a = a + H(b, c, d) + mj + ti;
        a = a << s | a >>> (32 - s);
        a += b;
        return a;
    }

    private int II(int a, int b, int c, int d, int mj, int s, int ti)
    {
        a = a + I(b, c, d) + mj + ti;
        a = a << s | a >>> (32 - s);
        a += b;
        return a;
    }

    private byte[] intToBytes(int n)
    {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++)
        {
            b[i] = (byte)((n >>> (i * 8)) & 0xFF);
        }
        return b;
    }
    
    public Md5()
    {
        Initialize();
    }
    
    public Md5(int nSpecialCode)
    {
        Initialize();
        D = nSpecialCode;
    }

    private void Initialize()
    {
        A = 0x67452301; //in memory, this is 0x01234567 
        B = 0xefcdab89; //in memory, this is 0x89abcdef 
        C = 0x98badcfe; //in memory, this is 0xfedcba98 
        D = 0x10325476; //in memory, this is 0x76543210 

        totalBytes = 0;
        remainBufferCount = 0;
    }
    
    private int[] uintBuffer16 = new int[16];
    private byte[] remainBuffer64 = new byte[64];
    private int remainBufferCount;
    private long totalBytes;
    
    private void InternalTransformBlock64(byte[] inputBuffer, int inputOffset)
    {
        for (int j = 0; j < uintBuffer16.length; j++)
        {
            uintBuffer16[j] = (int)(((inputBuffer[j * 4 + inputOffset] & 0x000000FF)) | 
                                    ((inputBuffer[j * 4 + inputOffset + 1] & 0x000000FF) << 8) | 
                                    ((inputBuffer[j * 4 + inputOffset + 2] & 0x000000FF) << 16) | 
                                     (inputBuffer[j * 4 + inputOffset + 3] & 0x000000FF) << 24);
        }

        int a = A, b = B, c = C, d = D;

        /* Round 1 */
        a = FF(a, b, c, d, uintBuffer16[0], S11, 0xd76aa471); /* 1 */
        d = FF(d, a, b, c, uintBuffer16[1], S12, 0xe8c7b752); /* 2 */
        c = FF(c, d, a, b, uintBuffer16[2], S13, 0x242070d3); /* 3 */
        b = FF(b, c, d, a, uintBuffer16[3], S14, 0xc1bdcee4); /* 4 */
        a = FF(a, b, c, d, uintBuffer16[4], S11, 0xf57c0fa5); /* 5 */
        d = FF(d, a, b, c, uintBuffer16[5], S12, 0x4787c626); /* 6 */
        c = FF(c, d, a, b, uintBuffer16[6], S13, 0xa8304617); /* 7 */
        b = FF(b, c, d, a, uintBuffer16[7], S14, 0xfd469508); /* 8 */
        a = FF(a, b, c, d, uintBuffer16[8], S11, 0x698098d9); /* 9 */
        d = FF(d, a, b, c, uintBuffer16[9], S12, 0x8b44f7aa); /* 10 */
        c = FF(c, d, a, b, uintBuffer16[10], S13, 0xffff5bbb); /* 11 */
        b = FF(b, c, d, a, uintBuffer16[11], S14, 0x895cd7bc); /* 12 */
        a = FF(a, b, c, d, uintBuffer16[12], S11, 0x6b90112d); /* 13 */
        d = FF(d, a, b, c, uintBuffer16[13], S12, 0xfd98719e); /* 14 */
        c = FF(c, d, a, b, uintBuffer16[14], S13, 0xa679438f); /* 15 */
        b = FF(b, c, d, a, uintBuffer16[15], S14, 0x49b40820); /* 16 */

        /* Round 2 */
        a = GG(a, b, c, d, uintBuffer16[1], S21, 0xf61e2562); /* 17 */
        d = GG(d, a, b, c, uintBuffer16[6], S22, 0xc040b340); /* 18 */
        c = GG(c, d, a, b, uintBuffer16[11], S23, 0x265e5a51); /* 19 */
        b = GG(b, c, d, a, uintBuffer16[0], S24, 0xe9b6c7aa); /* 20 */
        a = GG(a, b, c, d, uintBuffer16[5], S21, 0xd62f105d); /* 21 */
        d = GG(d, a, b, c, uintBuffer16[10], S22, 0x2441453); /* 22 */
        c = GG(c, d, a, b, uintBuffer16[15], S23, 0xd8a1e681); /* 23 */
        b = GG(b, c, d, a, uintBuffer16[4], S24, 0xe7d3fbc8); /* 24 */
        a = GG(a, b, c, d, uintBuffer16[9], S21, 0x21e1cde6); /* 25 */
        d = GG(d, a, b, c, uintBuffer16[14], S22, 0xc33707d6); /* 26 */
        c = GG(c, d, a, b, uintBuffer16[3], S23, 0xf4d50d87); /* 27 */
        b = GG(b, c, d, a, uintBuffer16[8], S24, 0x455a14ed); /* 28 */
        a = GG(a, b, c, d, uintBuffer16[13], S21, 0xa9e3e905); /* 29 */
        d = GG(d, a, b, c, uintBuffer16[2], S22, 0xfcefa3f8); /* 30 */
        c = GG(c, d, a, b, uintBuffer16[7], S23, 0x676f02d9); /* 31 */
        b = GG(b, c, d, a, uintBuffer16[12], S24, 0x8d2a4c8a); /* 32 */

        /* Round 3 */
        a = HH(a, b, c, d, uintBuffer16[5], S31, 0xfffa3942); /* 33 */
        d = HH(d, a, b, c, uintBuffer16[8], S32, 0x8771f681); /* 34 */
        c = HH(c, d, a, b, uintBuffer16[11], S33, 0x6d9d6122); /* 35 */
        b = HH(b, c, d, a, uintBuffer16[14], S34, 0xfde5380c); /* 36 */
        a = HH(a, b, c, d, uintBuffer16[1], S31, 0xa4beea44); /* 37 */
        d = HH(d, a, b, c, uintBuffer16[4], S32, 0x4bdecfa9); /* 38 */
        c = HH(c, d, a, b, uintBuffer16[7], S33, 0xf6bb4b60); /* 39 */
        b = HH(b, c, d, a, uintBuffer16[10], S34, 0xbebfbc70); /* 40 */
        a = HH(a, b, c, d, uintBuffer16[13], S31, 0x289b7ec6); /* 41 */
        d = HH(d, a, b, c, uintBuffer16[0], S32, 0xeaa127fa); /* 42 */
        c = HH(c, d, a, b, uintBuffer16[3], S33, 0xd4ef3085); /* 43 */
        b = HH(b, c, d, a, uintBuffer16[6], S34, 0x4881d05); /* 44 */
        a = HH(a, b, c, d, uintBuffer16[9], S31, 0xd9d4d039); /* 45 */
        d = HH(d, a, b, c, uintBuffer16[12], S32, 0xe6db99e5); /* 46 */
        c = HH(c, d, a, b, uintBuffer16[15], S33, 0x1fa27cf8); /* 47 */
        b = HH(b, c, d, a, uintBuffer16[2], S34, 0xc4ac5665); /* 48 */

        /* Round 4 */
        a = II(a, b, c, d, uintBuffer16[0], S41, 0xf4292244); /* 49 */
        d = II(d, a, b, c, uintBuffer16[7], S42, 0x432aff97); /* 50 */
        c = II(c, d, a, b, uintBuffer16[14], S43, 0xab9423a7); /* 51 */
        b = II(b, c, d, a, uintBuffer16[5], S44, 0xfc93a039); /* 52 */
        a = II(a, b, c, d, uintBuffer16[12], S41, 0x655b59c3); /* 53 */
        d = II(d, a, b, c, uintBuffer16[3], S42, 0x8f0ccc92); /* 54 */
        c = II(c, d, a, b, uintBuffer16[10], S43, 0xffeff47d); /* 55 */
        b = II(b, c, d, a, uintBuffer16[1], S44, 0x85845dd1); /* 56 */
        a = II(a, b, c, d, uintBuffer16[8], S41, 0x6fa87e4f); /* 57 */
        d = II(d, a, b, c, uintBuffer16[15], S42, 0xfe2ce6e0); /* 58 */
        c = II(c, d, a, b, uintBuffer16[6], S43, 0xa3014314); /* 59 */
        b = II(b, c, d, a, uintBuffer16[13], S44, 0x4e0811a1); /* 60 */
        a = II(a, b, c, d, uintBuffer16[4], S41, 0xf7537e82); /* 61 */
        d = II(d, a, b, c, uintBuffer16[11], S42, 0xbd3af235); /* 62 */
        c = II(c, d, a, b, uintBuffer16[2], S43, 0x2ad7d2bb); /* 63 */
        b = II(b, c, d, a, uintBuffer16[9], S44, 0xeb86d391); /* 64 */

        A += a;
        B += b;
        C += c;
        D += d;

        totalBytes += 64;
    }
    
    private int TransformBlock(byte[] inputBuffer, int inputOffset, int inputCount, byte[] outputBuffer, int outputOffset)
    {
        if (remainBufferCount > 0)
        {
            int readCount = Math.min(64 - remainBufferCount, inputCount);
            System.arraycopy(inputBuffer, inputOffset, remainBuffer64, remainBufferCount, readCount);
            inputOffset += readCount;
            inputCount -= readCount;
            remainBufferCount += readCount;
        }

        if (remainBufferCount >= 64)
        {
            InternalTransformBlock64(remainBuffer64, 0);
            remainBufferCount = 0;
        }

        if (inputCount > 0)
        {
            int inputOffsetEnd = inputOffset + inputCount;
            if (inputOffset < inputOffsetEnd)
            {
                for (int i = inputOffset; i <= inputOffsetEnd - 64; i += 64)
                {
                    InternalTransformBlock64(inputBuffer, i);
                }
            }

            remainBufferCount = inputCount % 64;
            if (remainBufferCount > 0)
            {
                System.arraycopy(inputBuffer, inputOffsetEnd - remainBufferCount, remainBuffer64, 0, remainBufferCount);
            }
        }

        if (outputBuffer != null && outputBuffer.length >= 16)
        {
            System.arraycopy(intToBytes(A), 0, outputBuffer, outputOffset, 4);
            System.arraycopy(intToBytes(B), 0, outputBuffer, outputOffset + 4, 4);
            System.arraycopy(intToBytes(C), 0, outputBuffer, outputOffset + 8, 4);
            System.arraycopy(intToBytes(D), 0, outputBuffer, outputOffset + 12, 4);
            return 16;
        }
        else
        {
            return 0;
        }
    }
    
    private byte[] HashFinal()
    {
        byte[] returnBuffer = new byte[16];
        byte[] finalBuffer;
        if (remainBufferCount < 56)
        {
            finalBuffer = remainBuffer64;
            finalBuffer[remainBufferCount] = (byte) 0x80;
            for (int i = remainBufferCount + 1; i < 64 - remainBufferCount - 8; i++)
            {
                finalBuffer[i] = 0;
            }
        }
        else
        {
            finalBuffer = new byte[128];
            System.arraycopy(remainBuffer64, 0, finalBuffer, 0, remainBufferCount);
            finalBuffer[remainBufferCount] = (byte) 0x80;
        }

        totalBytes = totalBytes + (long) remainBufferCount;
        long totalBits = totalBytes * 8;
        int i = finalBuffer.length - 1;
        finalBuffer[i--] = (byte) ((totalBits >>> 56) & 0xFF);
        finalBuffer[i--] = (byte) ((totalBits >>> 48) & 0xFF);
        finalBuffer[i--] = (byte) ((totalBits >>> 40) & 0xFF);
        finalBuffer[i--] = (byte) ((totalBits >>> 32) & 0xFF);
        finalBuffer[i--] = (byte) ((totalBits >>> 24) & 0xFF);
        finalBuffer[i--] = (byte) ((totalBits >>> 16) & 0xFF);
        finalBuffer[i--] = (byte) ((totalBits >>> 8) & 0xFF);
        finalBuffer[i--] = (byte) (totalBits & 0xFF);

        for (int idx = 0; idx < finalBuffer.length; idx += 64)
        {
            InternalTransformBlock64(finalBuffer, idx);
        }

        System.arraycopy(intToBytes(A), 0, returnBuffer, 0, 4);
        System.arraycopy(intToBytes(B), 0, returnBuffer, 4, 4);
        System.arraycopy(intToBytes(C), 0, returnBuffer, 8, 4);
        System.arraycopy(intToBytes(D), 0, returnBuffer, 12, 4);

        return returnBuffer;
    }
    
    private byte[] ComputeHash(byte[] data)
    {
        Initialize();
        TransformBlock(data, 0, data.length, null, 0);
        return HashFinal();
    }
    
    private byte[] ComputeHash(String filePathName, long fileLength)
    {
        if (filePathName == null ||
            !FileUtils.isExist(filePathName))
        {
            logger.e(filePathName + " file is invaild." );
            return null;
        }

        FileInputStream fin = null;
        try
        {
            Initialize();
            byte[] buffer = new byte[1024];
            int readCount = 0;
            long size = 0;
            
            fin = new FileInputStream(filePathName);
            while ((readCount = fin.read(buffer)) > 0)
            {
                TransformBlock(buffer, 0, readCount, null, 0);
                size += readCount;
                if (size >= fileLength)
                {
                    break;
                }
            }
            return HashFinal();
        }
        catch (Exception e)
        {
            logger.e("ComputeHash() has error.");
            e.printStackTrace();
            return null;
        }
        finally
        {
            try
            {
                if (fin != null)
                {
                    fin.close();
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
    
    private byte[] ComputeHashQuick(String filePathName, int blockCount, int blockLength)
    {
        if (filePathName == null || !FileUtils.isExist(filePathName))
        {
            logger.e(filePathName + " file is invaild.");
            return null;
        }
        else if (blockCount <= 0)
        {
            return ComputeHash(filePathName, FileUtils.getFileLength(filePathName));
        }
        else if (blockLength <= 0)
        {
            logger.e("blockLength is invalid.");
            return null;
        }

        RandomAccessFile accessWriteFile = null;
        try
        {
            Initialize();
            byte[] buffer = new byte[blockLength];
            long fileLen = FileUtils.getFileLength(filePathName);
            
            // 打开目标存储文件
            accessWriteFile = new RandomAccessFile(filePathName, "r");
            
            // 文件头块
            accessWriteFile.seek(0);
            int readCount = accessWriteFile.read(buffer, 0, blockLength);
            TransformBlock(buffer, 0, readCount, null, 0);
            
            if (blockCount > 1)
            {
                if (blockCount > 2)
                {
                    long startPos = 0;
                    long blockOffset = fileLen / (blockCount - 1);
                    for (int i = 0; i < blockCount - 2; i++)
                    {
                        accessWriteFile.seek(0);   //重新定位到文件头
                        startPos += blockOffset; 
                        accessWriteFile.seek(startPos);
                        readCount = accessWriteFile.read(buffer, 0, blockLength);
                        TransformBlock(buffer, 0, readCount, null, 0);
                        startPos += readCount;
                        if (startPos >= fileLen)
                        {
                            break;
                        }
                    }
                }

                //文件尾块
                if (fileLen > blockLength)
                {
                    accessWriteFile.seek(0);   //重新定位到文件头
                    accessWriteFile.seek((fileLen - blockLength));
                    readCount = accessWriteFile.read(buffer, 0, blockLength);
                    TransformBlock(buffer, 0, readCount, null, 0);
                }
            }
            
            return HashFinal();
        }
        catch (Exception e)
        {
            logger.e("ComputeHashQuick() has error.");
            e.printStackTrace();
            return null;
        }
        finally
        {
            // 关闭目标文件
            try
            {
                if (accessWriteFile != null)
                {
                    accessWriteFile.close();
                    accessWriteFile = null;
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
    
    private String Convert2String(byte[] md5Data)
    {
        if (md5Data == null)
        {
            return ("0000");    
        }
        
        // 把密文转换成十六进制的字符串形式
        StringBuffer sBuffer = new StringBuffer();
        for (int i = 0; i < md5Data.length; i++)
        {
            int tmpByte = md5Data[i];
            if (tmpByte < 0)
            {
                tmpByte += 256;  // 转换成无符号数
            }
            String tmpStr = strHexDigits[tmpByte / 16] + strHexDigits[tmpByte % 16];
            sBuffer.append(tmpStr);
        }
        
        return sBuffer.toString(); 
    }
    
    public String ComputeMd5(String strFilePathName)
    {
        byte[] md5Value = ComputeHash(strFilePathName, FileUtils.getFileLength(strFilePathName));
        return Convert2String(md5Value);
    }
    
    public String ComputeMd5(byte[] data)
    {
        byte[] md5Value = ComputeHash(data);
        return Convert2String(md5Value);
    }
    
    public String ComputeFileMd5(String strFilePathName)
    {
        String retValue = null;
        long fileLen = FileUtils.getFileLength(strFilePathName);
        if (fileLen > 3072)
        {
            retValue = Convert2String(ComputeHashQuick(strFilePathName, 3, 1024));
        }
        else
        {
            retValue = Convert2String(ComputeHash(strFilePathName, fileLen));
        }
        
        return retValue;
    }
}
