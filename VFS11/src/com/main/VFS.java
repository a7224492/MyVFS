package com.main;

import java.io.IOException;
import java.io.RandomAccessFile;

public class VFS
{

	public static void main(String[] args)
	{
		byte[] b1 = new byte[50];
		byte[] b2 = new byte[50];
		for (int i = 0; i < 50; ++i){
			b1[i] = 97;
			b2[i] = 98;
		}
		System.arraycopy(b1, 0, b2, 0, 20);
		
		for (int i = 0; i < b1.length; ++i){
			System.out.print(b1[i]+" ");
		}
		System.out.println();
		for (int i = 0; i < b2.length; ++i){
			System.out.print(b2[i]+" ");
		}
	}

}
