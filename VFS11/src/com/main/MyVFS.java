package com.main;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MyVFS
{
	public final static int BLOCK_SIZE = 4 * 1024;//

	public final static int MAX_INODE_NUM = 1000;//
	public final static int HASH_SIZE = MyVFS.MAX_INODE_NUM * 256;
	public final static int MAX_BLOCK_NUM = 1024 * 1024;
	public final static int MAX_INODE_BLOCK_NUM = 100 * 1024;
	public final static int MAX_DIR_CHILD_NUM = 200;
	public final static int MAX_OPEN_FILE_NUM = MyVFS.MAX_INODE_NUM * 2;

	public final static int INODE_SIZE = 16 + 4 * MyVFS.MAX_INODE_BLOCK_NUM;
	public final static int SUPER_BLOCK_SIZE = 4 + 4 + 4 * MyVFS.MAX_BLOCK_NUM;
	public final static int FILE_CACHE_SIZE = MyVFS.BLOCK_SIZE;

	public final static int SUPER_BLOCK_BEGIN_POS = MyVFS.HASH_SIZE;
	public final static int INODE_BEGIN_POS = MyVFS.SUPER_BLOCK_BEGIN_POS + MyVFS.SUPER_BLOCK_SIZE;
	public final static int BLOCK_BEGIN_POS = MyVFS.INODE_BEGIN_POS + MyVFS.INODE_SIZE * MyVFS.MAX_INODE_NUM;

	public final static int FILE_TYPE_FILE = 1;
	public final static int FILE_TYPE_DIRECTORY = 0;

	public final static int FILE_MODE_READ = 1;
	public final static int FILE_MODE_WRITE = 2;
	public final static int FILE_MODE_RDWR = 3;

	public SuperBlock superBlock;
	public Inode[] inodes;
	public InodeHash inodeHash;
	public String vfsFilePath;
	public Dir[] dirTable;
	public MyVFSFile[] openFileTable;
	public int openFileNum;

	public MyVFS()
	{
		this.superBlock = new SuperBlock();
		this.inodes = new Inode[MyVFS.MAX_INODE_NUM];
		this.inodeHash = new InodeHash();
		dirTable = new Dir[MyVFS.MAX_INODE_NUM];
		for (int i = 0; i < MyVFS.MAX_INODE_NUM; ++i)
		{
			dirTable[i] = new Dir();
		}
		this.openFileTable = new MyVFSFile[MyVFS.MAX_OPEN_FILE_NUM];
		this.openFileNum = 0;
	}

	public boolean init(String pathname)
	{
		File file = new File(pathname);

		if (pathname == null || pathname.equals(""))
		{
			System.out.println("init() error: pathname is null");
			return false;
		}

		if (!file.isAbsolute())
		{
			System.out.println("init() error: pathname is not effective path");
			return false;
		}

		if (file.exists())
		{
			if (!file.isFile())
			{
				System.out.println("init() error: pathname is not a file");
			}
			RandomAccessFile raf = null;

			try
			{
				raf = new RandomAccessFile(file, "rw");
				FileChannel chan = raf.getChannel();
				MappedByteBuffer buffer = chan.map(FileChannel.MapMode.READ_ONLY, 0, MyVFS.BLOCK_BEGIN_POS);
				this.inodeHash.readFromFile(buffer);
				this.superBlock = new SuperBlock();
				this.superBlock.readFromFile(buffer);

				for (int i = 0; i < this.inodes.length; ++i)
				{
					this.inodes[i] = new Inode();
					this.inodes[i].readFromFile(buffer);
				}
				this.createDirTable();
				this.printAll();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			finally
			{
				try
				{
					if (raf != null)
					{
						raf.close();
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}
		else
		{
			try
			{
				file.createNewFile();
				this.format(pathname);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		this.vfsFilePath = pathname;

		return true;
	}

	public MyVFSFile openFile(String vfsPathname, int mode)
	{
		MyVFSFile retFile = null;
		if (vfsPathname == null || vfsPathname.equals(""))
		{
			System.out.println("openFile() error: vfsPathname is null");
			return null;
		}
		if (vfsPathname.charAt(0) != '/')
		{
			System.out.println("openFile() error: vfsPathanme is not effective path");
			return null;
		}

		if (this.openFileNum >= MyVFS.MAX_OPEN_FILE_NUM)
		{
			System.out.println("openFile() error: open file num is max");
			return null;
		}

		InodeHashEntry inodeEntry = this.inodeHash.findInode(vfsPathname);
		if (inodeEntry == null)
		{
			System.out.println("openFile() error: vfsPathname file doesn't exist");
			return null;
		}

		int index = inodeEntry.inodeIndex;

		if (this.openFileTable[index] == null)
		{
			retFile = new MyVFSFile(this);
			retFile.inodeIndex = index;
			retFile.mode = mode;
			this.openFileTable[index] = retFile;
		}
		else
		{
			retFile = this.openFileTable[index];
		}

		return retFile;
	}

	public void createFile(String vfsPathname, int type)
	{
		if (vfsPathname == null || vfsPathname.equals(""))
		{
			System.out.println("createFile() error: vfsPathname is null");
			return;
		}

		if (vfsPathname.charAt(0) != '/')
		{
			System.out.println("createFile() error: vfsPathname is not effective path");
			return;
		}

		InodeHashEntry inodeEntry = this.inodeHash.findInode(vfsPathname);
		if (inodeEntry != null)
		{
			System.out.println("createFile() error: vfsPathname exist");
			return;
		}

		if (this.superBlock.inodeNumInUse >= MyVFS.MAX_INODE_BLOCK_NUM)
		{
			System.out.println("createFile() error: has no free inode");
			return;
		}
		this.mkdir(vfsPathname, type);
	}

	public boolean deleteFile(MyVFSFile file)
	{
		if (file == null)
		{
			System.out.println("deleteFile() error: file is null");
			return false;
		}
		if (file.inodeIndex < 0 || file.inodeIndex >= MyVFS.MAX_INODE_NUM)
		{
			System.out.println("deleteFile() error: file doesn't exist");
			return false;
		}

		this.deleteDir(file.inodeIndex);

		return true;
	}

	public int writeFile(MyVFSFile vfsFile, byte[] data, int off, int len)
	{
		if (vfsFile == null)
		{
			System.out.println("writeFile() error: vfsFile is null");
			return -1;
		}

		if (vfsFile.inodeIndex < 0 || vfsFile.inodeIndex > MyVFS.MAX_INODE_NUM)
		{
			System.out.println("writeFile() error: vfsFile is closed");
			return -1;
		}

		if (off < 0 || off >= data.length || (off + len) > data.length || len < 0)
		{
			System.out.println("writeFile() error: data overflow");
			return -1;
		}

		if (this.inodes[vfsFile.inodeIndex].type == 0)
		{
			System.out.println("writeFile() error: vfsFile is a directory");
			return -1;
		}

		if (this.inodes[vfsFile.inodeIndex].isUse == 0)
		{
			System.out.println("writeFile() error: vfsFile doesn't exist");
			return -1;
		}

		if ((vfsFile.mode & MyVFS.FILE_MODE_WRITE) == 0)
		{
			System.out.println("writeFile() error: vfsFile has no write mode");
			return -1;
		}

		int nwrite = 0;
		Inode inode = this.inodes[vfsFile.inodeIndex];

		if (vfsFile.dataLen != 0)
		{
			this.flushFile(vfsFile);
		}

		vfsFile.dataStartPos = vfsFile.filePointerPos;
		if (len > vfsFile.data.length)
		{
			System.arraycopy(data, off, vfsFile.data, 0, vfsFile.data.length);
			vfsFile.dataLen = vfsFile.data.length;
			nwrite = this.writeDataToBlock(inode,
				vfsFile.filePointerPos + vfsFile.dataLen,
				data,
				off + vfsFile.dataLen,
				len - vfsFile.dataLen);
		}
		else
		{
			System.arraycopy(data, off, vfsFile.data, 0, len);
			nwrite = len;
			vfsFile.dataLen += len;
		}
		if (inode.size < (vfsFile.filePointerPos + nwrite))
		{
			inode.size = vfsFile.filePointerPos + nwrite;
		}
		vfsFile.filePointerPos += len;

		return nwrite;
	}

	public int readFile(MyVFSFile vfsFile, byte[] data, int off, int len)
	{
		if (vfsFile == null)
		{
			System.out.println("readFile() error: vfsFile is null");
			return -1;
		}
		if (data == null)
		{
			System.out.println("readFile() error: data is null");
			return -1;
		}
		if (off < 0 || off > data.length || len > data.length || (off + len) > data.length)
		{
			System.out.println("readFile() error: data overflow");
			return -1;
		}
		if (vfsFile.inodeIndex < 0 || vfsFile.inodeIndex >= MyVFS.MAX_INODE_NUM)
		{
			System.out.println("readFile() error: vfsFile is not open");
			return -1;
		}
		if (vfsFile.filePointerPos < 0)
		{
			System.out.println("readFile() error: vfsFile filePointerPos is negative");
			return -1;
		}
		if ((vfsFile.mode & MyVFS.FILE_MODE_READ) == 0)
		{
			System.out.println("readFile() error: vfsFile has no read mode");
			return -1;
		}

		int readLen = 0;
		Inode inode = this.inodes[vfsFile.inodeIndex];
		if ((vfsFile.filePointerPos + len) > inode.size)
		{
			System.out.println("readFile() error: read range out file size");
			return -1;
		}

		if (vfsFile.dataLen == 0)
		{
			readLen = this.readDataFromBlock(inode, vfsFile.filePointerPos, data, off, len);
			int dataLen = len > vfsFile.data.length ? vfsFile.data.length : len;
			System.arraycopy(data, off, vfsFile.data, 0, dataLen);
			vfsFile.filePointerPos += len;
		}
		else
		{
			if ((vfsFile.filePointerPos + len - 1) < vfsFile.dataStartPos
				|| vfsFile.filePointerPos > (vfsFile.dataStartPos + vfsFile.dataLen - 1))
			{
				readLen = this.readDataFromBlock(inode, vfsFile.filePointerPos, data, off, len);
				vfsFile.filePointerPos += len;
			}
			else
			{
				int leftOff = 0;
				int leftLen = 0;
				int middleOff = 0;
				int middleLen = 0;
				int rightOff = 0;
				int rightLen = 0;
				if (vfsFile.filePointerPos < vfsFile.dataStartPos)
				{
					leftOff = vfsFile.filePointerPos;
					leftLen = vfsFile.dataStartPos - vfsFile.filePointerPos;
				}
				else
				{
					middleOff = vfsFile.filePointerPos - vfsFile.dataStartPos;
				}
				int tmp = vfsFile.filePointerPos + len;
				if (tmp > (vfsFile.dataStartPos + vfsFile.dataLen))
				{
					rightOff = vfsFile.dataStartPos + vfsFile.dataLen - 1;
					middleLen = vfsFile.dataLen;
					rightLen = len - leftLen - middleLen;
				}
				else
				{
					middleLen = len - leftLen;
				}
				this.readDataFromBlock(inode, leftOff, data, off, leftLen);
				this.readDataFromBlock(inode, rightOff, data, off + leftLen + middleLen, rightLen);
				for (int i = middleOff; i < middleLen; ++i)
				{
					data[off + leftLen] = vfsFile.data[i];
				}
				readLen += len;
				vfsFile.filePointerPos += len;
			}
		}

		return readLen;
	}

	public void addDiskFile(String pathname, String dirpath)
	{
		if (pathname == null || pathname.equals(""))
		{
			System.out.println("addDiskFile() error: pathname is null");
			return;
		}

		if (dirpath == null || dirpath.equals(""))
		{
			System.out.println("addDiskFile() error: vfsPathname is null");
			return;
		}

		File addFile = new File(pathname);
		if (!addFile.exists())
		{
			System.out.println("addDiskFile() error: pathname doesn't exist");
			return;
		}

		String name = dirpath + "/" + addFile.getName();

		InodeHashEntry ihe = this.inodeHash.findInode(name);
		if (ihe != null)
		{
			System.out.println("addDiskFile() error: vfsPathname exist");
			return;
		}

		this.addFile(addFile, dirpath);
	}

	public boolean updateFile(String pathname, String filepath)
	{
		if (pathname == null || pathname.equals(""))
		{
			System.out.println("updateFile() error: pathname is null");
			return false;
		}
		if (filepath == null || filepath.equals(""))
		{
			System.out.println("updateFile() error: filepath is null");
			return false;
		}

		File diskFile = new File(pathname);
		if (!diskFile.exists())
		{
			System.out.println("updateFile() error: diskFile doesn't exist");
			return false;
		}
		if (!diskFile.isFile())
		{
			System.out.println("updateFile() error: diskFile is not file");
			return false;
		}

		MyVFSFile file = this.openFile(filepath, MyVFS.FILE_MODE_RDWR);
		if (file == null)
		{
			System.out.println("updateFile() error: pathname is not in vfs");
			return false;
		}

		if (diskFile.length() > Integer.MAX_VALUE)
		{
			System.out.println("updateFile() error: pathname is too large");
			return false;
		}

		this.adjustFileSize(file, (int)diskFile.length());
		RandomAccessFile raf = null;
		try
		{
			raf = new RandomAccessFile(diskFile, "rw");
			byte[] data = new byte[1024];
			int nread = 0;
			while ((nread = raf.read(data)) != -1)
			{
				this.writeFile(file, data, 0, nread);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (raf != null)
				{
					raf.close();
					;
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		return true;
	}

	public boolean adjustFileSize(MyVFSFile file, int newSize)
	{
		if (file == null)
		{
			System.out.println("adjustFileSize() error: file is null");
			return false;
		}
		if (file.inodeIndex < 0 || file.inodeIndex > MyVFS.MAX_INODE_NUM)
		{
			System.out.println("adjustFileSize() error: file is not opened");
			return false;
		}

		boolean ret = false;

		Inode inode = this.inodes[file.inodeIndex];
		int needBlockNum = newSize / MyVFS.BLOCK_SIZE + newSize % MyVFS.BLOCK_SIZE == 0 ? 0 : 1;

		if (needBlockNum > inode.blockNum)
		{
			ret = this.allocateBlock(inode, needBlockNum - inode.blockNum);
		}
		else if (needBlockNum < inode.blockNum)
		{
			ret = this.freeBlock(inode, inode.blockNum - needBlockNum);
		}

		inode.size = newSize;
		ret = true;
		return ret;
	}

	public void format(String pathname)
	{
		if (pathname == null || pathname.equals(""))
		{
			System.out.println("format() error: pathname is null");
			return;
		}

		File file = new File(pathname);

		if (!file.exists())
		{
			System.out.println("format() error: pathname is not exist");
			return;
		}

		RandomAccessFile raf = null;

		try
		{
			raf = new RandomAccessFile(pathname, "rw");
			raf.seek(MyVFS.BLOCK_BEGIN_POS - 1);
			raf.write(0);
			raf.seek(0);
			FileChannel chan = raf.getChannel();
			MappedByteBuffer buf = chan.map(FileChannel.MapMode.READ_WRITE, 0, MyVFS.BLOCK_BEGIN_POS);
			InodeHashEntry rootEntry = this.inodeHash.insertHashInode("/");
			int x = rootEntry.inodeIndex;
			dirTable[x].name = "/";
			dirTable[x].inodeIndex = 0;
			this.inodeHash.writeToFile(buf);
			this.superBlock.inodeNumInUse = 1;
			this.superBlock.writeToFile(buf);
			for (int i = 0; i < this.inodes.length; ++i)
			{
				if (i == x)
				{
					this.inodes[i] = new Inode();
					this.inodes[i].type = 0;
					this.inodes[i].size = 0;
					this.inodes[i].blockNum = 0;
					this.inodes[i].isUse = 1;
				}
				else
				{
					this.inodes[i] = new Inode();
				}
				this.inodes[i].writeToFile(buf);
			}
			buf.force();
			chan.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (raf != null)
				{
					raf.close();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void exit()
	{
		RandomAccessFile raf = null;

		try
		{
			raf = new RandomAccessFile(this.vfsFilePath, "rw");
			FileChannel chan = raf.getChannel();
			MappedByteBuffer buf = chan.map(FileChannel.MapMode.READ_WRITE, 0, MyVFS.BLOCK_BEGIN_POS);
			for (int i = 0; i < this.openFileTable.length; ++i)
			{
				if (this.openFileTable[i] != null)
				{
					this.flushFile(this.openFileTable[i]);
				}
			}
			this.inodeHash.writeToFile(buf);
			this.superBlock.writeToFile(buf);
			for (int i = 0; i < this.inodes.length; ++i)
			{
				this.inodes[i].writeToFile(buf);
			}
			chan.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (raf != null)
				{
					raf.close();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public boolean closeFile(MyVFSFile file)
	{
		if (file == null)
		{
			System.out.println("closeFile() error: file is null");
			return false;
		}

		if (file.inodeIndex < 0 || file.inodeIndex >= MyVFS.MAX_INODE_NUM)
		{
			System.out.println("closeFile() error: file doesn't exist");
			return false;
		}

		if (file.dataLen < 0 || file.dataLen > MyVFS.FILE_CACHE_SIZE)
		{
			System.out.println("closeFile() error: file cache data len error");
			return false;
		}

		MyVFSFile f;
		f = this.openFileTable[file.inodeIndex];
		if (f == null)
		{
			System.out.println("closeFile() error: file is not in openFileTable");
			return false;
		}
		this.openFileTable[file.inodeIndex] = null;

		if (this.inodes[file.inodeIndex].type == MyVFS.FILE_TYPE_FILE)
		{
			if (file.dataLen == 0)
			{
				file.inodeIndex = -1;
			}
			else
			{
				this.flushFile(file);
				file.inodeIndex = -1;
			}
		}
		else if (this.inodes[file.inodeIndex].type == MyVFS.FILE_TYPE_DIRECTORY)
		{
			file.inodeIndex = -1;
		}

		return false;
	}

	public void flushFile(MyVFSFile vfsFile)
	{
		if (vfsFile == null)
		{
			return;
		}

		if (this.inodes[vfsFile.inodeIndex].isUse == 0)
		{
			return;
		}
		if (vfsFile.dataLen == 0)
		{
			return;
		}

		Inode inode = this.inodes[vfsFile.inodeIndex];

		this.writeDataToBlock(inode, vfsFile.dataStartPos, vfsFile.data, 0, vfsFile.dataLen);

		vfsFile.dataLen = 0;
	}

	public void listDir()
	{
		InodeHashEntry entry = this.inodeHash.findInode("/");
		Dir root = this.dirTable[entry.inodeIndex];
		this.printDirRec(root, 1);
	}

	private boolean allocateBlockByDataLen(Inode inode, int dataLen)
	{
		if (inode == null)
		{
			return false;
		}
		if (dataLen < 0)
		{
			return false;
		}

		int remain = inode.blockNum * MyVFS.BLOCK_SIZE - inode.size;
		int remainDataLen = dataLen - remain;
		int needBlockNum = remainDataLen / MyVFS.BLOCK_SIZE + 1;

		return allocateBlock(inode, needBlockNum);
	}

	private boolean allocateBlock(Inode inode, int blockNum)
	{
		if (inode == null)
		{
			System.out.println("allocateBlock() error: inode is null");
			return false;
		}

		if (blockNum < 0)
		{
			System.out.println("allocateBlock() error: blockNum is nagetive");
			return false;
		}

		if (blockNum == 0)
		{
			return false;
		}

		if (blockNum > (MyVFS.MAX_BLOCK_NUM - this.superBlock.blockNumInUse))
		{
			System.out.println("allocateBlock() error: no free space");
			return false;
		}

		int num = 0;
		int[] blocks = new int[blockNum];
		int i;
		for (i = 0; i < MyVFS.MAX_BLOCK_NUM; ++i)
		{
			if (this.superBlock.blocks[i] == 0)
			{
				if ((num + inode.blockNum) >= MyVFS.MAX_INODE_BLOCK_NUM)
				{
					System.out.println("allocateBlock() error: inode has no more block");
					return false;
				}
				blocks[num++] = i;
				if (num == blockNum)
				{
					break;
				}
			}
		}

		if (i == MyVFS.MAX_BLOCK_NUM)
		{
			System.out.println("allocateBlock() error: no free space");
			return false;
		}

		for (int j = 0; j < num; ++j)
		{
			inode.blocks[inode.blockNum + j] = blocks[j];
			this.superBlock.blocks[blocks[j]] = 1;
		}

		this.superBlock.blockNumInUse += num;
		inode.blockNum += num;

		return true;
	}

	private boolean freeBlock(Inode inode, int blockNum)
	{
		if (inode == null)
		{
			System.out.println("freeBlock() error: inode is null");
			return false;
		}
		if (blockNum < 0 || blockNum > inode.blockNum)
		{
			System.out.println("freeBlock() error: blockNum error");
			return false;
		}
		if (blockNum == 0)
		{
			return true;
		}

		for (int i = inode.blockNum - 1; i > (inode.blockNum - 1 - blockNum); --i)
		{
			int blockIndex = inode.blocks[i];
			this.superBlock.blocks[blockIndex] = 0;
		}
		this.superBlock.blockNumInUse -= blockNum;
		inode.blockNum -= blockNum;

		return true;
	}

	private int writeDataToBlock(Inode inode, int pos, byte[] data, int off, int len)
	{
		if (inode == null)
		{
			System.out.println("writeDataToBlock() error: inode is null");
			return -1;
		}

		if (data == null)
		{
			System.out.println("writeDataToBlock() error: data is null");
			return -1;
		}

		if (off < 0 || off >= data.length || (off + len) > data.length || len > data.length)
		{
			System.out.println("writeDataToBlock() error: data overflow");
			return -1;
		}

		if ((inode.blockNum * MyVFS.BLOCK_SIZE) < (pos + 1 + len))
		{
			int remainData = (pos + len) - (inode.blockNum * MyVFS.BLOCK_SIZE);
			if (!this.allocateBlockByDataLen(inode, remainData))
			{
				System.out.println("writeDataToBlock() error: has no free block");
				return -1;
			}
		}

		int startBlockNum = pos / MyVFS.BLOCK_SIZE;
		int offset = pos % MyVFS.BLOCK_SIZE;

		RandomAccessFile raf = null;
		int nwrite = 0;
		try
		{
			nwrite = 0;
			raf = new RandomAccessFile(this.vfsFilePath, "rw");
			raf.seek(MyVFS.BLOCK_BEGIN_POS + MyVFS.BLOCK_SIZE * inode.blocks[startBlockNum] + offset);
			if (len <= (MyVFS.BLOCK_SIZE - offset))
			{
				raf.write(data, off, len);
				nwrite += len;
			}
			else
			{
				raf.write(data, off, MyVFS.BLOCK_SIZE - offset + 1);
				nwrite += MyVFS.BLOCK_SIZE - offset + 1;
				len -= nwrite;
			}
			int i = startBlockNum + 1;
			while (nwrite < len)
			{
				int tmp = len - nwrite;
				raf.seek(MyVFS.BLOCK_BEGIN_POS + MyVFS.BLOCK_SIZE * inode.blocks[i]);
				if (tmp < MyVFS.BLOCK_SIZE)
				{
					raf.write(data, off + nwrite, tmp);
					nwrite += len;
				}
				else
				{
					raf.write(data, off + nwrite, MyVFS.BLOCK_SIZE);
					nwrite += MyVFS.BLOCK_SIZE;
				}
				++i;
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (raf != null)
				{
					raf.close();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return nwrite;
	}

	private int readDataFromBlock(Inode inode, int pos, byte[] data, int off, int len)
	{
		if (inode == null)
		{
			System.out.println("readDataFromBlock() error: inode is null");
			return -1;
		}
		if (data == null)
		{
			System.out.println("readDataFromBlock() error: data is null");
			return -1;
		}
		if (off < 0 || off >= data.length || len > data.length || (off + len) > data.length)
		{
			System.out.println("readDataFromBlock() error: data overflow");
			return -1;
		}
		if ((pos + len) > inode.size)
		{
			System.out.println("readDataFromBlock() error: read range out file size");
			return -1;
		}
		if (len == 0)
		{
			return 0;
		}

		RandomAccessFile raf = null;

		int startBlock = pos / MyVFS.BLOCK_SIZE;
		int offset = pos % MyVFS.BLOCK_SIZE;

		int nread = 0;

		try
		{
			raf = new RandomAccessFile(this.vfsFilePath, "rw");
			raf.seek(MyVFS.BLOCK_BEGIN_POS + MyVFS.BLOCK_SIZE * inode.blocks[startBlock] + offset);
			if (len <= (MyVFS.BLOCK_SIZE - offset + 1))
			{
				raf.read(data, off, len);
				nread += len;
			}
			else
			{
				raf.read(data, off, MyVFS.BLOCK_SIZE - offset + 1);
				nread += MyVFS.BLOCK_SIZE - offset + 1;
			}
			int i = startBlock + 1;
			while (nread < len)
			{
				int tmp = len - nread;
				raf.seek(MyVFS.BLOCK_BEGIN_POS + MyVFS.BLOCK_SIZE * inode.blocks[i]);
				if (tmp < MyVFS.BLOCK_SIZE)
				{
					raf.read(data, off + nread, tmp);
					nread += tmp;
				}
				else
				{
					raf.read(data, off + nread, MyVFS.BLOCK_SIZE);
					nread += MyVFS.BLOCK_SIZE;
				}
				++i;
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (raf != null)
				{
					raf.close();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		return nread;
	}

	private void createDirTableRec(int i, String pathname)
	{
		InodeHashEntry inodeEntry = this.inodeHash.findInode(pathname);
		if (inodeEntry == null)
		{
			return;
		}

		short index = (short)inodeEntry.inodeIndex;
		boolean isRoot = pathname.equals("/");
		Dir parent = this.dirTable[index];
		if (!parent.isUse)
		{
			parent.isUse = true;
			if (!isRoot)
			{
				int lastIndex = pathname.lastIndexOf('/');
				parent.name = pathname.substring(lastIndex + 1, pathname.length());
			}
			else
			{
				parent.name = pathname;
			}
			parent.inodeIndex = index;
		}
		Dir child = this.dirTable[i];
		if (child.parent != index)
		{
			parent.childList.insert(i);
		}

		child.parent = index;
		if (!isRoot)
		{
			int lastIndex = pathname.lastIndexOf('/');
			if (lastIndex == 0)
			{
				createDirTableRec(index, "/");
			}
			else
			{
				createDirTableRec(index, pathname.substring(0, lastIndex));
			}
		}
		else
		{
			return;
		}
	}

	private void createDirTable()
	{
		for (int i = 0; i < this.inodeHash.table.length; ++i)
		{
			if (this.inodeHash.table[i] != null)
			{
				if (this.dirTable[i].isUse)
				{
					continue;
				}
				String pathname = this.inodeHash.table[i].getVfsPathname();
				if (pathname.equals("/"))
				{
					Dir dir = this.dirTable[i];
					dir.name = "/";
					dir.inodeIndex = (short)i;
					dir.isUse = true;
					continue;
				}
				int lastIndex = pathname.lastIndexOf('/');
				Dir d = this.dirTable[i];
				d.name = pathname.substring(lastIndex + 1, pathname.length());
				d.inodeIndex = (short)i;
				d.isUse = true;
				if (lastIndex == 0)
				{
					createDirTableRec(i, "/");
				}
				else
				{
					createDirTableRec(i, pathname.substring(0, lastIndex));
				}
			}
		}
	}

	private void addFile(File file, String dirpath)
	{
		if (file == null)
		{
			System.out.println("addFile() error: file is null");
			return;
		}

		if (dirpath == null || dirpath.equals(""))
		{
			System.out.println("addFile() error: dirpath is null");
			return;
		}

		String name = file.getName();
		if (file.isFile())
		{
			this.createFile(dirpath + "/" + name, MyVFS.FILE_TYPE_FILE);
			MyVFSFile vfsFile = this.openFile(dirpath + "/" + name, MyVFS.FILE_MODE_RDWR);
			RandomAccessFile raf = null;
			try
			{
				raf = new RandomAccessFile(file, "rw");
				byte[] data = new byte[1024];
				int nread = 0;
				while ((nread = raf.read(data)) != -1)
				{
					this.writeFile(vfsFile, data, 0, nread);
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			finally
			{
				try
				{
					if (raf != null)
					{
						raf.close();
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}
		else if (file.isDirectory())
		{
			this.createFile(dirpath + "/" + name, MyVFS.FILE_TYPE_DIRECTORY);
			File[] files = file.listFiles();
			for (int i = 0; i < files.length; ++i)
			{
				addFile(files[i], dirpath + "/" + name);
			}
		}
	}

	private boolean mkdir(String vfsPathname, int type)
	{
		if (vfsPathname == null)
		{
			System.out.println("mkdir() error: vfsPathname is null");
			return false;
		}

		if (vfsPathname.charAt(0) != '/')
		{
			System.out.println("mkdir() error: vfsPathname is not effective path");
			return false;
		}

		InodeHashEntry addInodeEntry = this.inodeHash.findInode(vfsPathname);
		if (addInodeEntry != null)
		{
			return true;
		}

		String parent;
		int lastIndex = vfsPathname.lastIndexOf('/');
		if (lastIndex == 0)
		{
			parent = "/";
		}
		else
		{
			parent = vfsPathname.substring(0, lastIndex);
		}

		InodeHashEntry inodeEntry = this.inodeHash.findInode(parent);
		if (inodeEntry == null)
		{
			mkdir(parent, MyVFS.FILE_TYPE_DIRECTORY);
		}
		inodeEntry = this.inodeHash.findInode(parent);
		int i = inodeEntry.inodeIndex;
		Dir dir = this.dirTable[i];
		if (dir.childList.num >= MyVFS.MAX_DIR_CHILD_NUM)
		{
			System.out.println("mkdir() error: current parent dir is full");
			return false;
		}

		addInodeEntry = this.inodeHash.insertHashInode(vfsPathname);
		Dir addDir = this.dirTable[addInodeEntry.inodeIndex];
		addDir.name = vfsPathname.substring(lastIndex + 1, vfsPathname.length());
		addDir.isUse = true;
		addDir.inodeIndex = (short)addInodeEntry.inodeIndex;
		addDir.parent = (short)i;

		Inode inode = this.inodes[addInodeEntry.inodeIndex];
		inode.isUse = 1;
		inode.type = type;

		this.superBlock.inodeNumInUse += 1;

		dir.childList.insert(addInodeEntry.inodeIndex);

		return true;
	}

	private void deleteDir(int index)
	{
		if (index < 0 || index >= MyVFS.MAX_INODE_NUM)
		{
			System.out.println("deleteDir() error: file is null");
			return;
		}

		Inode inode = this.inodes[index];

		if (inode.type == MyVFS.FILE_TYPE_DIRECTORY)
		{
			Dir dir = this.dirTable[index];
			for (int i = dir.childList.firstNodeValue(); i != dir.childList.end(); i = dir.childList.nextValue())
			{
				Dir child = this.dirTable[i];
				deleteDir(child.inodeIndex);
			}
		}
		this.freeInode(inode, index);
	}

	private boolean freeInode(Inode inode, int index)
	{
		if (inode == null)
		{
			System.out.println("freeInode() error: inode is null");
			return false;
		}
		if (inode.isUse == 0)
		{
			System.out.println("freeInode() error: inode is not in use");
			return false;
		}

		if (inode.type == MyVFS.FILE_TYPE_FILE)
		{
			for (int i = 0; i < inode.blockNum; ++i)
			{
				int k = inode.blocks[i];
				this.superBlock.blocks[k] = 0;
				if (this.superBlock.blockNumInUse <= 0)
				{
					System.out.println("freeNode() error: no block is use");
					return false;
				}
				this.superBlock.blockNumInUse -= 1;
			}
			inode.blockNum = 0;
			inode.isUse = 0;
			inode.size = 0;
			inode.type = -1;
		}
		else if (inode.type == MyVFS.FILE_TYPE_DIRECTORY)
		{
			inode.isUse = 0;
			inode.type = -1;
		}

		Dir dir = this.dirTable[index];
		Dir parent;
		if (dir.parent != -1)
		{
			parent = this.dirTable[dir.parent];
			parent.childList.delete(index);
		}
		dir.childList = null;
		dir.inodeIndex = -1;
		dir.isUse = false;
		dir.name = "";
		dir.parent = -1;

		if (this.superBlock.inodeNumInUse <= 0)
		{
			System.out.println("freeNode() error: no inode is use");
			return false;
		}
		this.superBlock.inodeNumInUse -= 1;

		InodeHashEntry ihe = this.inodeHash.table[index];

		ihe.inodeIndex = -1;
		ihe.isUse = -1;
		ihe.pathLen = 0;
		ihe.setVfsPathname("");

		return true;
	}

	private void printDirRec(Dir dir, int level)
	{
		String spaceStr = "";
		for (int i = 0; i < level; ++i)
		{
			spaceStr += "  ";
		}

		for (int i = dir.childList.firstNodeValue(); i != dir.childList.end(); i = dir.childList.nextValue())
		{
			Dir d = this.dirTable[i];
			Inode inode = this.inodes[d.inodeIndex];
			if (inode.type == MyVFS.FILE_TYPE_FILE)
			{
				System.out.println(spaceStr + d.name + "(" + inode.size + ")");
			}
			else
			{
				System.out.println(spaceStr + d.name);
			}

			if (this.inodes[d.inodeIndex].type == 0)
			{
				printDirRec(d, level + 1);
			}
		}
	}

	public void printAll()
	{
		System.out.println(this.superBlock.blockNumInUse + "," + this.superBlock.inodeNumInUse);

		for (int i = 0; i < this.superBlock.blocks.length; ++i)
		{
			if (this.superBlock.blocks[i] == 1)
			{
				System.out.println(i);
			}
		}

		for (int i = 0; i < this.inodes.length; ++i)
		{
			System.out.println(this.inodes[i].type + "," + this.inodes[i].size + "," + this.inodes[i].blockNum + ","
				+ this.inodes[i].blocks[0] + "," + this.inodes[i].isUse);
		}
	}
}

class MyVFSFile
{
	public int inodeIndex;
	public int mode;
	public int filePointerPos;
	public int dataLen;
	public int dataStartPos;
	public MyVFS vfs;
	public byte[] data;

	public MyVFSFile(MyVFS vfs)
	{
		this.inodeIndex = -1;
		this.mode = MyVFS.FILE_MODE_READ;
		this.filePointerPos = 0;
		this.dataLen = 0;
		this.dataStartPos = 0;
		this.data = new byte[MyVFS.FILE_CACHE_SIZE];
		this.vfs = vfs;
	}

	public long getFileSize()
	{
		if (inodeIndex < 0 || inodeIndex >= MyVFS.MAX_INODE_NUM)
		{
			System.out.println("getFileSize() error: file is null");
			return -1;
		}
		Inode inode = vfs.inodes[inodeIndex];
		return inode.size;
	}

}

class SuperBlock
{
	public int inodeNumInUse;
	public int blockNumInUse;
	public int[] blocks;

	public SuperBlock()
	{
		this.inodeNumInUse = 0;
		this.blockNumInUse = 0;
		this.blocks = new int[MyVFS.MAX_BLOCK_NUM];
	}

	public void writeToFile(MappedByteBuffer raf)
		throws IOException
	{
		raf.putInt(inodeNumInUse);
		raf.putInt(blockNumInUse);
		for (int i = 0; i < blocks.length; ++i)
		{
			raf.putInt(this.blocks[i]);
		}
	}

	public void readFromFile(MappedByteBuffer raf)
		throws IOException
	{
		this.inodeNumInUse = raf.getInt();
		this.blockNumInUse = raf.getInt();
		for (int i = 0; i < this.blocks.length; ++i)
		{
			this.blocks[i] = raf.getInt();
		}
	}
}

class Inode
{
	public int type; // 0 is directory, 1 is file
	public int size;
	public int blockNum;
	public int isUse;
	public int[] blocks;

	public Inode()
	{
		this.type = -1;
		this.size = 0;
		this.blockNum = 0;
		this.isUse = 0;
		this.blocks = new int[MyVFS.MAX_INODE_BLOCK_NUM];
	}

	public void writeToFile(MappedByteBuffer raf)
		throws IOException
	{
		raf.putInt(type);
		raf.putInt(size);
		raf.putInt(blockNum);

		for (int i = 0; i < MyVFS.MAX_INODE_BLOCK_NUM; ++i)
		{
			raf.putInt(this.blocks[i]);
		}

		raf.putInt(isUse);
	}

	public void readFromFile(MappedByteBuffer raf)
		throws IOException
	{
		this.type = raf.getInt();
		this.size = raf.getInt();
		this.blockNum = raf.getInt();
		for (int i = 0; i < MyVFS.MAX_INODE_BLOCK_NUM; ++i)
		{
			this.blocks[i] = raf.getInt();
		}
		this.isUse = raf.getInt();
	}
}

class InodeHashEntry
{
	public char[] vfsPathname;
	public int pathLen;
	public int isUse;
	public int inodeIndex;

	public InodeHashEntry()
	{
		this.vfsPathname = new char[244];
		this.pathLen = 0;
		this.isUse = 0;
		this.inodeIndex = -1;
	}

	public void setVfsPathname(String vfsPathname)
	{
		for (int i = 0; i < vfsPathname.length(); ++i)
		{
			this.vfsPathname[i] = vfsPathname.charAt(i);
		}
		this.pathLen = vfsPathname.length();
	}

	public String getVfsPathname()
	{
		String str = new String(vfsPathname, 0, this.pathLen);
		return str;
	}

	public void writeToFile(MappedByteBuffer raf)
		throws IOException
	{
		byte[] bytes = new byte[this.vfsPathname.length];
		for (int i = 0; i < bytes.length; ++i)
		{
			bytes[i] = (byte)this.vfsPathname[i];
		}
		raf.put(bytes);
		raf.putInt(this.pathLen);
		raf.putInt(this.isUse);
		raf.putInt(this.inodeIndex);
	}

	public void readFromFile(MappedByteBuffer raf)
		throws IOException
	{
		byte[] bytes = new byte[244];
		raf.get(bytes);
		for (int i = 0; i < bytes.length; ++i)
		{
			this.vfsPathname[i] = (char)bytes[i];
		}
		this.pathLen = raf.getInt();
		this.isUse = raf.getInt();
		this.inodeIndex = raf.getInt();
	}
}

class InodeHash
{
	public InodeHashEntry[] table;

	public InodeHash()
	{
		this.table = new InodeHashEntry[MyVFS.MAX_INODE_NUM];
	}

	public long HashString(String vfsPathname)
	{
		int hash = 0;
		int x = 0;
		char[] str = vfsPathname.toCharArray();

		int i = 0;
		while (i < str.length)
		{
			hash = (hash << 4) + (str[i++]);
			if ((x = (int)(hash & 0xF0000000L)) != 0)
			{
				hash ^= (x >> 24);
				hash &= ~x;
			}
		}

		return (hash & 0x7FFFFFFF) % this.table.length;
	}

	public InodeHashEntry findInode(String vfsPathname)
	{
		if (vfsPathname == null)
		{
			return null;
		}

		int index = (int)this.HashString(vfsPathname);

		int i = index;
		if (this.table[i] != null && this.table[i].getVfsPathname().equals(vfsPathname))
		{
			return table[i];
		}
		else
		{
			++i;
			while (this.table[i] == null || !this.table[i].getVfsPathname().equals(vfsPathname))
			{
				i = (i + 1) % this.table.length;
				if (i == index)
				{
					break;
				}
			}

			if (i == index)
			{
				return null;
			}
			else
			{
				return this.table[i];
			}
		}
	}

	public InodeHashEntry insertHashInode(String vfsPathname)
	{
		if (vfsPathname == null)
		{
			return null;
		}

		int index = (int)this.HashString(vfsPathname);

		if (table[index] == null)
		{
			table[index] = new InodeHashEntry();
			table[index].setVfsPathname(vfsPathname);
			table[index].inodeIndex = index;
			table[index].isUse = 1;

			return table[index];
		}
		else
		{
			int i = (index + 1) % this.table.length;
			while (table[i] != null && i != index)
			{
				i = (i + 1) % table.length;
			}
			if (i == index)
			{
				System.out.println("insertHashInode() error: table is full");
				return null;
			}
			table[i] = new InodeHashEntry();
			table[i].setVfsPathname(vfsPathname);
			table[i].inodeIndex = i;
			table[i].isUse = 1;

			return table[i];
		}
	}

	public void writeToFile(MappedByteBuffer raf)
		throws IOException
	{
		for (int i = 0; i < this.table.length; ++i)
		{
			if (table[i] != null)
			{
				this.table[i].writeToFile(raf);
			}
			else
			{
				InodeHashEntry ihe = new InodeHashEntry();
				ihe.writeToFile(raf);
			}
		}
	}

	public void readFromFile(MappedByteBuffer raf)
		throws IOException
	{
		for (int i = 0; i < this.table.length; ++i)
		{
			int currPos = raf.position();
			raf.position(currPos + 248);
			int isUse = raf.getInt();
			if (isUse == 1)
			{
				raf.position(currPos);
				this.table[i] = new InodeHashEntry();
				this.table[i].readFromFile(raf);
			}
			else
			{
				raf.position(currPos + 256);
			}
		}
	}

	public void printAll()
	{
		for (int i = 0; i < table.length; ++i)
		{
			if (table[i] != null)
			{
				System.out.println(table[i].inodeIndex + "," + table[i].getVfsPathname());
			}
		}
	}
}

class Dir
{
	public String name;
	public int inodeIndex;
	public IntList childList;
	public int parent;
	public boolean isUse;

	public Dir()
	{
		this.childList = new IntList();
		this.name = "";
		this.inodeIndex = -1;
		this.parent = -1;
		this.isUse = false;
	}
}

class IntListNode
{
	public int value;
	public IntListNode next;

	public IntListNode()
	{
		this.value = 0;
		this.next = null;
	}
}

class IntList
{
	public IntListNode head;
	public int num;
	public IntListNode checkNode;

	public IntList()
	{
		head = new IntListNode();
		num = 0;
		checkNode = head;
	}

	public boolean insert(int value)
	{
		IntListNode addNode = new IntListNode();
		addNode.value = value;

		IntListNode node = this.head;
		while (node.next != null)
		{
			node = node.next;
		}
		node.next = addNode;
		num++;
		return true;
	}

	public boolean delete(int value)
	{
		IntListNode node = this.head;
		while (node.next != null)
		{
			if (node.next.value == value)
			{
				node.next = node.next.next;
				break;
			}
			node = node.next;
		}
		if (node.next == null)
		{
			return false;
		}
		num--;
		return true;
	}

	public int firstNodeValue()
	{
		checkNode = head.next;
		int ret;
		if (checkNode == null)
		{
			return -1;
		}
		else
		{
			ret = checkNode.value;
		}

		return ret;
	}

	public int nextValue()
	{
		int ret;
		if (checkNode.next == null)
		{
			return -1;
		}
		else
		{
			ret = checkNode.next.value;
			checkNode = checkNode.next;
		}
		return ret;
	}

	public int end()
	{
		return -1;
	}
}