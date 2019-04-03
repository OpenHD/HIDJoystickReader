package com.none.non.hidreader;

import android.text.InputFilter;
import android.webkit.WebView;

/**
 * Created by user2 on 3/26/2019.
 */

public class DescriptorParser {


    public int NumbersOfAxis = 0;
    public int AxisOffset = 0;
    public int AxisMinValue = 0;
    public int AxisMaxValue = 0;
    public int AxisReportSize = 0;
    public int AxisReportCount = 0;


    public int ButtonsArraySize = 0;
    public int ButtonsOffset = 0;
    public int ButtonsFound = 0;

    public void Parse(byte[] InBuffer)
    {
        int LastFound = 0;
        int FoundAt = 0;
        int Offset = 0;

        int BufferSize = InBuffer.length;

        for(int i=0; i<BufferSize-1; i++)
        {
            //Look for INPUT (Data,Var,Abs) ( 0x81, 0x02 )

            //we can`t check even, data format can be value1 + value2 and in case of
            //Logical Min and Logical Max can be val1+val2 or +val3 or +val4
            if(InBuffer[i]  == (byte)0x81 && InBuffer[i+1] == 0x02 || InBuffer[i]  == (byte)0x81 && InBuffer[i+1] == 0x42)
            {
                //Lets try to look up to find if it is a Button
                int Buttons = IsButton(i-2, LastFound, InBuffer );
                if(Buttons >= 1 )
                {
                    int TmpFindReportSize = FindReportSize(i-2, LastFound, InBuffer );
                    int TmpReportCount = FindReportCount(i-2, LastFound, InBuffer );

                    ButtonsArraySize = TmpFindReportSize * TmpReportCount;
                    ButtonsOffset = Offset;
                    ButtonsFound = Buttons;
                }

                //Lets try to look up  find if it is a Axisto
                int Axis =  IsAxis(i-2, LastFound, InBuffer ) ;
                if(Axis >= 1 )
                {
                    NumbersOfAxis = Axis;
                    AxisOffset = Offset;
                    AxisMinValue = FindMin(i-2, LastFound, InBuffer );
                    AxisMaxValue =  FindMax(i-2, LastFound, InBuffer );
                    AxisReportSize = FindReportSize(i-2, LastFound, InBuffer );
                    AxisReportCount = FindReportCount(i-2, LastFound, InBuffer );

                }

                //
                int FindReportSize = FindReportSize(i-2, LastFound, InBuffer );
                int ReportCount = FindReportCount(i-2, LastFound, InBuffer );
                Offset += FindReportSize * ReportCount;

                LastFound = i+1;
            }
        }
    }

    public Boolean IsAxisValid()
    {
        int ArraySize = AxisReportCount * AxisReportSize;
        if(ArraySize < 1)
            return false;

        if(NumbersOfAxis == 0)
            return false;

        if(AxisMaxValue == 0)
            return false;

        return true;
    }

    public Boolean IsButtonsValid()
    {
        if(ButtonsFound == 0)
            return false;

        if(ButtonsArraySize == 0)
            return false;

        return true;
    }

    private int IsButton(int LookFrom, int LastFound, byte[] InBuffer)
    {
        int ButtonsFound = 0;
        while(LookFrom != LastFound)
        {
            //Check if button: USAGE_PAGE (Button) (0x05, 0x09 )
            if(InBuffer[LookFrom]  == 0x05 && InBuffer[LookFrom+1] == 0x09)
            {
                //Button.
                ButtonsFound +=1;
            }

            LookFrom = LookFrom -1;
        }
        return ButtonsFound;
    }

    private int IsAxis(int LookFrom, int LastFound, byte[] InBuffer)
    {
        int AxisFound = 0;
        while(LookFrom != LastFound)
        {

            //Check if Axis: USAGE_PAGE (Button) (0x05, 0x09 )
            //        0x09, 0x30,                    //         USAGE (X)
            //        0x09, 0x31,                    //         USAGE (Y)
           //         0x09, 0x32,                    //         USAGE (Z)
           //         0x09, 0x33,                    //         USAGE (Rx)
           //         0x09, 0x34,                    //         USAGE (Ry)
          //          0x09, 0x35,                    //         USAGE (Rz)
          //          0x09, 0x36,                    //         USAGE (Slider)
            if(  InBuffer[LookFrom]  == 0x09 && InBuffer[LookFrom+1] == 0x30 ||
                    InBuffer[LookFrom]  == 0x09 && InBuffer[LookFrom+1] == 0x31 ||
                    InBuffer[LookFrom]  == 0x09 && InBuffer[LookFrom+1] == 0x32 ||
                    InBuffer[LookFrom]  == 0x09 && InBuffer[LookFrom+1] == 0x33 ||
                    InBuffer[LookFrom]  == 0x09 && InBuffer[LookFrom+1] == 0x34 ||
                    InBuffer[LookFrom]  == 0x09 && InBuffer[LookFrom+1] == 0x35 ||
                    InBuffer[LookFrom]  == 0x09 && InBuffer[LookFrom+1] == 0x36
               //     InBuffer[LookFrom]  == 0x05 && InBuffer[LookFrom+1] == 0x?? || What else?
            //        InBuffer[LookFrom]  == 0x05 && InBuffer[LookFrom+1] == 0x?? ||
                    )
            {
                //Axis.
                AxisFound += 1;
            }

            LookFrom = LookFrom -1;
        }
        return AxisFound;
    }

    private int FindMin(int LookFrom, int LastFound, byte[] InBuffer)
    {
        //Logical Minimum :
        //0x15  0x00			1 byte
        //0x16, 0x00, 0x25,		2 bytes
        //0x17, 0x00, 0x25, 0x20, 0x95	4 bytes
        int MinValue = -1;

        while(LookFrom != LastFound)
        {
            //check 1 byte value
            if(  InBuffer[LookFrom] == 0x15 )
            {
              //  MinValue = ( (byte) InBuffer[LookFrom+1] ) & 0xff;
                MinValue = ( (byte) InBuffer[LookFrom+1] ) ;
            }
            //check 2 byte value
            if(  InBuffer[LookFrom] == 0x16 )
            {
                MinValue = ( (InBuffer[LookFrom+2] & 0xFF) << 8) | ( InBuffer[LookFrom+1] & 0xFF);
            }
            //check 4 byte value
            if(  InBuffer[LookFrom] == 0x17 )
            {
                MinValue = ((InBuffer[LookFrom+1] & 0xFF) <<  0) |
                        ((InBuffer[LookFrom+2] & 0xFF) <<  8) |
                        ((InBuffer[LookFrom+3] & 0xFF) << 16) |
                        ((InBuffer[LookFrom+4] & 0xFF) << 24);
            }
            LookFrom = LookFrom -1;
        }
        return MinValue;
    }

    private int FindMax(int LookFrom, int LastFound, byte[] InBuffer)
    {
        //LOGICAL_MAXIMUM :
        //0x25, 0x20			1 byte  max
        //0x26, 0x20, 0x95,		2 bytes
        //0x27, 0x20, 0x95, 0x18, 0x75,	4 bytes
        int MaxValue = -1;

        while(LookFrom != LastFound)
        {
            //check 1 byte value
            if(  InBuffer[LookFrom] == 0x25 )
            {//
               // MaxValue = ( (int) InBuffer[LookFrom+1] ) & 0xff;
                MaxValue = ( (byte) InBuffer[LookFrom+1] );
            }
            //check 2 byte value
            if(  InBuffer[LookFrom] == 0x26 )
            {
                MaxValue = ( (InBuffer[LookFrom+2] & 0xFF) << 8) | ( InBuffer[LookFrom+1] & 0xFF);
            }
            //check 4 byte value
            if(  InBuffer[LookFrom] == 0x27 )
            {
                MaxValue = ((InBuffer[LookFrom+1] & 0xFF) <<  0) |
                        ((InBuffer[LookFrom+2] & 0xFF) <<  8) |
                        ((InBuffer[LookFrom+3] & 0xFF) << 16) |
                        ((InBuffer[LookFrom+4] & 0xFF) << 24);
            }
            LookFrom = LookFrom -1;
        }
        return MaxValue;
    }

    private  int FindReportSize(int LookFrom, int LastFound, byte[] InBuffer)
    {
        int reportSize = -1;
        //0x75, 0x08,        //     Report Size (8)
        while (LookFrom != LastFound)
        {
            if (InBuffer[LookFrom] == 0x75)
            {
                reportSize = ((int) InBuffer[LookFrom + 1]) & 0xff;
            }
            LookFrom = LookFrom - 1;
        }
        return reportSize;
    }

    private  int FindReportCount(int LookFrom, int LastFound, byte[] InBuffer)
    {
        int reportCount = -1;
        // 0x95, 0x04,        //     Report Count (4)
        while (LookFrom != LastFound)
        {
            if (InBuffer[LookFrom] == (byte)0x95)
            {
                reportCount = ((int) InBuffer[LookFrom + 1]) & 0xff;
            }
            LookFrom = LookFrom - 1;
        }
        return reportCount;
    }
}
