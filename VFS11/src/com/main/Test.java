package com.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class Test
{

	public static void main(String[] args) throws Exception
	{
		MyVFS myvfs = new MyVFS();
		myvfs.init("D:/VFS/vfs10");
		myvfs.createFile("/A/AA/a", MyVFS.FILE_TYPE_FILE);
		myvfs.createFile("/A/BB/b", MyVFS.FILE_TYPE_FILE);
		myvfs.createFile("/B/BB/BBB/b", MyVFS.FILE_TYPE_FILE);
		myvfs.createFile("/C/CCCC/a", MyVFS.FILE_TYPE_FILE);
		myvfs.createFile("/A/AA/b", MyVFS.FILE_TYPE_FILE);
//		
//		MyVFSFile f = myvfs.openFile("/A/AA/a", MyVFS.FILE_MODE_RDWR);
//		byte[] data = new byte[1045];
//		for (int i = 0; i < 1024; ++i){
//			data[i] = 101;
//		}
//		for (int i = 1024; i < 1045; ++i){
//			data[i] = 100;
//		}
//		f.filePointerPos = 10;
//		myvfs.writeFile(f, data, 0, 1045);
//		
//		MyVFSFile file = myvfs.openFile("/B/BB/BBB/b", MyVFS.FILE_MODE_RDWR);
//		byte[] data3 = new byte[2033];
//		for (int i = 0; i < 2033; ++i){
//			data3[i] = 102;
//		}
//		myvfs.writeFile(file, data3, 0, 2033);
//		byte[] data4 = new byte[100];
//		for (int i = 0; i < 100; ++i){
//			data4[i] = 'g';
//		}
//		
//		file.filePointerPos = 100;
//		myvfs.writeFile(file, data4, 0, 10);
//		file.filePointerPos = (int)file.getFileSize();
//		byte[] data5 = new byte[1055];
//		for (int i = 0 ; i < 1055; ++i){
//			data5[i] = 'h';
//		}
//		myvfs.writeFile(file, data5, 0, 1055);
		
//		byte[] data = new byte[1055];
//		MyVFSFile f = myvfs.openFile("/B/BB/BBB/b", MyVFS.FILE_MODE_RDWR);
//		myvfs.readFile(f, data, 0, 1055);
		
//		myvfs.closeFile(f);
		
		myvfs.addDiskFile("d:/VFS/a.txt", "/A/AA");
		myvfs.addDiskFile("d:/VFS/b.txt", "/A/AA");
		myvfs.addDiskFile("d:/VFS/c.txt", "/A/AA");
		myvfs.addDiskFile("d:/VFS/d.txt", "/A/AA");
		myvfs.addDiskFile("d:/VFS/test", "/B/BB");
		myvfs.updateFile("d:/VFS/add.txt", "/B/BB/test/test2/aaa.txt");
		
//		MyVFSFile file = myvfs.openFile("/B/BB/test", MyVFS.FILE_MODE_RDWR);
//		
//		myvfs.deleteFile(file);
		
		myvfs.listDir();
		myvfs.exit();
		
//		InodeHash hash = new InodeHash();
//		hash.insertHashInode("/");
//		hash.insertHashInode("/A");
//		hash.insertHashInode("/AAA");
//		hash.insertHashInode("/A/AA");
//		hash.insertHashInode("/BB/BB/BBB");
//		hash.insertHashInode("/C/CCC/CC");
//		
//		RandomAccessFile raf = null;
//		
//		try{
//			raf = new RandomAccessFile("d:/VFS/hashTest", "rw");
//			raf.seek(99);
//			raf.write(0);
//		}catch(IOException e){
//			e.printStackTrace();
//		}finally{
//			try{
//				if (raf != null){
//					raf.close();
//				}
//			}catch(IOException e){
//				e.printStackTrace();
//			}
//		}
		
//		char[] ch = new char[10];
//		for (int i = 0; i < ch.length; ++i){
//			ch[i] = 0;
//		}
//		for (int i = 0; i < 5; ++i){
//			ch[i] = 99;
//		}
//		String str = new String(ch);
//		
//		System.out.println(str.length());
		
//		MyVFS myvfs = new MyVFS();
//		myvfs.init("d:/VFS/vfs1");
//		myvfs.createFile("/A/AA/a", 1);
//		myvfs.createFile("/B/BB/BBB/BBBB", 0);
//		MyVFSFile file = myvfs.openFile("/A/AA/a", MyVFS.FILE_READ_MODE|MyVFS.FILE_WRITE_MODE);
//		byte[] data1 = new byte[1027];
//		for (int i = 0; i < data1.length; ++i){
//			data1[i] = 102;
//		}
//		myvfs.writeFile(file, data1, 0, data1.length);
//		myvfs.flushFile(file);
//		
//		myvfs.createFile("/A/AA/b", 1);
//		MyVFSFile file2 = myvfs.openFile("/A/AA/b", MyVFS.FILE_READ_MODE|MyVFS.FILE_WRITE_MODE);
//		byte[] data2 = new byte[1027];
//		for (int i = 0; i < data2.length; ++i){
//			data2[i] = 103;
//		}
//		myvfs.writeFile(file2, data2, 0, data2.length);
//		myvfs.flushFile(file2);
//		
//		myvfs.deleteFile(file2);
//		
//		myvfs.listDir();
//		myvfs.exit();
		
//		byte[] data = new byte[10];
//		myvfs.readFile(file, data, 0, 10);
//		for (int i = 0; i < data.length; ++i){
//			System.out.println(data[i]);
//		}
//		Thread.sleep(20000);
		
//		File file = new File("d:/VFS/class");
//		FileOutputStream fos = null;
//		ObjectOutputStream oos = null;
//		
//		Student stu = new Student(20, 001,"jiangzhen", "jinping");
//		
//		try{
//			fos = new FileOutputStream(file);
//			oos = new ObjectOutputStream(fos);
//			oos.writeObject(stu);
//		}catch(IOException e){
//			e.printStackTrace();
//		}finally{
//			try{
//				if (fos != null){
//					fos.close();
//				}
//			}catch(IOException e){
//				e.printStackTrace();
//			}
//		}
	}

}