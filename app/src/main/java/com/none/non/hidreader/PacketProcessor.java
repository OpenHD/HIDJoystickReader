package com.none.non.hidreader;

import java.lang.*;

/**
 * Created by user2 on 3/27/2019.
 */

public class PacketProcessor {

    byte[] InPacket;
    public byte[] SendPacket = new byte[21]; //2 bytes per values. bytes 1-16 axis, bytes - 17-18 buttons

    private int ButtonsArraySize = 0;
    private int ButtonsOffset = 0;

    private int AxisOffset = 0;
    private int AxisReportSize = 0;
    private int AxisReportCount = 0;
    int AxisMinVal = 0;
    int AxisMaxVal = 0;
    int AxisCount = 0;

    int[] AxisRawValuesArr = new int[16];
    public int[] AxisNormalized = new int[16];


    PacketProcessor(int InBufferSize,int inButtonsArraySize, int inButtonsOffset,int inAxisOffset, int inAxisReportSize, int inAxisReportCount, int inAxisMinVal, int inAxisMaxVal, int inAxisCount)
    {
        InPacket = new byte[InBufferSize];
        ButtonsArraySize = inButtonsArraySize;
        ButtonsOffset = inButtonsOffset;

        AxisOffset = inAxisOffset;
        AxisReportSize = inAxisReportSize;
        AxisReportCount = inAxisReportCount;
        AxisMinVal = inAxisMinVal;
        AxisMaxVal = inAxisMaxVal;
        AxisCount = inAxisCount;

        for(int i =0;i<16;i++)
        {
            AxisRawValuesArr[i] = 0;
            AxisNormalized[i] = 0;
        }
    }

    private void NormalizeAxis()
    {
        if(AxisMaxVal == 255 && AxisMinVal == 0)
        {
            for(int i =0;i<AxisCount;i++)
            {
                AxisNormalized[i] = AxisRawValuesArr[i] -127;
                if(AxisNormalized[i] >= 0)
                {
                    AxisNormalized[i] = (  (AxisNormalized[i]) * 256) - 1;
                    AxisNormalized[i] =   (int) (( (  AxisNormalized[i]+32768.0)/65.536)+1000);

                }
                else
                {
                    AxisNormalized[i] = (  (AxisNormalized[i]-1) * 256) + 1;
                    AxisNormalized[i] =  (int)  (( (  AxisNormalized[i]+32768.0)/65.536)+1000);
                }
            }
        }

        if(AxisMaxVal == 256 && AxisMinVal == 0)
        {
            for(int i =0;i<AxisCount;i++)
            {
                AxisNormalized[i] = AxisRawValuesArr[i] -128;
                if(AxisNormalized[i] >= 0)
                {
                    AxisNormalized[i] = (  (AxisNormalized[i]) * 256) - 1;
                    AxisNormalized[i] = (int)  (( (  (AxisNormalized[i])+32768.0)/65.536)+1000);
                }
                else
                {
                    AxisNormalized[i] = (  (AxisNormalized[i]) * 256) + 1;
                    AxisNormalized[i] = (int)  (( (  (AxisNormalized[i])+32768.0)/65.536)+1000);
                }
            }
        }

        if(AxisMaxVal == 127 && AxisMinVal == -127)
        {
            for(int i =0;i<AxisCount;i++)
            {
                if(AxisRawValuesArr[i] >= 0)
                {
                    AxisNormalized[i] = (  (AxisRawValuesArr[i]+1) * 256) - 1;
                    AxisNormalized[i] =  (int)(( (  (AxisNormalized[i])+32768.0)/65.536)+1000);
                }
                else
                {
                    AxisNormalized[i] = (  (AxisRawValuesArr[i]-1) * 256) + 1;
                    AxisNormalized[i] =  (int)(( (  (AxisNormalized[i])+32768.0)/65.536)+1000);
                }
            }
        }

    }

    public void ProcessNewPacket(byte[] packet,int ChannelOrderOneToEight[])
    {
        int InPacketByteLen = packet.length;

        //check if Packet not out of range
        if( (InPacketByteLen*8) >= (AxisReportSize*AxisReportCount + AxisOffset) ||
                (InPacketByteLen*8) >= (ButtonsArraySize + ButtonsOffset)   )
        {

            int tmpOffsetAxis = AxisOffset;
            for(int i=0;i<AxisReportCount; i++)
            {
                String tmp = GetStr(tmpOffsetAxis, tmpOffsetAxis+AxisReportSize-1, packet);
                AxisRawValuesArr[i] = (byte) Integer.parseInt(tmp, 2);

                if(AxisReportSize <= 8)
                {
                    if(AxisMinVal < 0)
                    {// we are in range -127 to +127
                    }
                    else
                    { // we are in rage 0-255
                        AxisRawValuesArr[i] = (byte) AxisRawValuesArr[i] & 0xFF;
                    }
                }
                //ADD CODE TO PARSE 2 and 4 bytes Values!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                tmpOffsetAxis += AxisReportSize;
            }

            NormalizeAxis();

            int pointer = 0;
            for(int i=0;i<AxisCount;i++)
            {
                pointer = ChannelOrderOneToEight[i];
                pointer = pointer -1;

                SendPacket[pointer*2] =  (byte)(  (int)AxisNormalized[i] & 0xFF);
                SendPacket[pointer*2+1] =  (byte)(  ( (int)AxisNormalized[i] >> 8) & 0xFF);
            }


            String ButtonsBitString = GetStr(ButtonsOffset, ButtonsOffset+ButtonsArraySize-1,packet);
            int ButtonsInt = Integer.parseInt(ButtonsBitString, 2);


            SendPacket[18] = 0;
            SendPacket[19] = 1;

            SendPacket[18] = 0;
            SendPacket[19] = 1;
            SendPacket[19] = (byte)(  (int)ButtonsInt & 0xFF);
            SendPacket[20] = (byte)(  ( (int)ButtonsInt >> 8) & 0xFF);

        }
    }

    private String GetStr(int from, int to, byte[] in)
    {  //byte0: bit7 bit6 bit5.. bit0, byte1: bit7,...bit0
        int bitmask[] = new int[] {1,2,4,8,16,32,64,128};
        int BitsToRead = to - from;
        byte result[] = new byte[BitsToRead+1];
        int counter = BitsToRead;

        while (BitsToRead != 0)
        {
            BitsToRead = to - from;
            int StartByteIndex = from/8;
            int BitOffset = StartByteIndex*8;
            int StartFromInBit = from - BitOffset;
            //int BitsTillByteEnd = 8 - StartFromInBit;

            int tempPos = StartFromInBit;
            if ((in[StartByteIndex] & bitmask[tempPos]) == 0)
            {
                result[counter] = '0';
            }
            else
            {
                result[counter] = '1';
                //result[0] |= 1 << tempPos;
            }

            counter = counter-1;
            from = from +1;

        }
        return  new String(result);
        //return result;
    }










}
