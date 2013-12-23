import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

import com.mojang.nbt.*;


public class Main {
	
	private static final int NUM_THREADS = 6;
	private static ExecutorService EXECUTOR;
	
	private static void findRecursive(File base, List<String> addTo, String prefix) {
		if(!prefix.equals(""))
			addTo.add(prefix);
		if(base.isDirectory())
			for(String fn : base.list())
				findRecursive(new File(base, fn), addTo, (prefix.equals("") ? fn : prefix + "/" + fn));
	}
	private static void recursiveDelete(File base) {
		if(base.isDirectory())
			for(String fn : base.list())
				recursiveDelete(new File(base, fn));
		base.delete();
	}
	private static List<String> findRecursive(File base) {
		List<String> rv = new java.util.ArrayList<String>();
		findRecursive(base, rv, "");
		return rv;
	}
	
	private static List<Pair<IdSpec, IdSpec>> blocks = new java.util.ArrayList<Pair<IdSpec, IdSpec>>();
	private static List<Pair<IdSpec, IdSpec>> items = new java.util.ArrayList<Pair<IdSpec, IdSpec>>();
	private static IdSpec lookup(int id, int meta, List<Pair<IdSpec, IdSpec>> list) {
		for(Pair<IdSpec, IdSpec> entry : list)
			if(entry.x.matches(id, meta))
				return entry.y;
		return null; //new IdSpec(id, meta);
	}
	public static IdSpec lookupBlock(int id, int meta) {return lookup(id, meta, blocks);}
	public static IdSpec lookupItem(int id, int meta) {return lookup(id, meta, items);}
	
	public static void main(String[] args) throws Exception{
		//System.in.read();
		
		long start = System.nanoTime();
		
		for(int k = 0; k < 1; k++) {
			final File indir = new File(args[0]);
			final File outdir = new File(args[1]);
			
			Scanner s = new Scanner(new File(args[2]));
			while(s.hasNextLine()) {
				String l = s.nextLine();
				if(l.equals(""))
					continue;
				l = l.split("#")[0].trim();
				if(l.equals(""))
					continue;
				
				String[] parts = l.split(" ");
				if(parts.length != 3 || (!parts[0].equals("block") && !parts[0].equals("item")))
					throw new Exception("invalid line: "+l);
				
				IdSpec from = IdSpec.parse(parts[1]);
				IdSpec to = IdSpec.parse(parts[2]);
				
				if(parts[0].equals("block")) {
					from.assertBlock(true);
					to.assertBlock(true);
					blocks.add(new Pair<IdSpec, IdSpec>(from, to));
				} else if(parts[0].equals("item")) {
					from.assertItem(true);
					to.assertItem(true);
					items.add(new Pair<IdSpec, IdSpec>(from, to));
				}
			}
			s.close();
			
			recursiveDelete(outdir);
			outdir.mkdirs();
			
			List<String> files = findRecursive(indir);
			
			EXECUTOR = Executors.newFixedThreadPool(NUM_THREADS);
			
			for(final String f : files) {
				System.out.print(f+" ");
				
				if(new File(indir, f).isDirectory()) {
					System.out.println("dir");
					if(!new File(outdir, f).mkdir())
						throw new Exception("failed to create: "+new File(outdir, f));
					
				} else if(f.startsWith("players/") || f.endsWith(".dat")) {
					convertNBTFile(new File(indir, f), new File(outdir, f));
					
				} else if(f.endsWith(".mca")) {
					System.out.print("mca\t");
					if(NUM_THREADS == 1)
						convertAnvilFile(new File(indir, f), new File(outdir, f));
					else {
						EXECUTOR.execute(new Runnable() {
							@Override
							public void run() {
								try {
									convertAnvilFile(new File(indir, f), new File(outdir, f));
								} catch(Exception e) {
									throw new RuntimeException(e);
								}
							}
						});
					}
					
				} else {
					System.out.println("unknown");
					copyFile(new File(indir, f), new File(outdir, f));
				}
			}
			
			EXECUTOR.shutdown();
			EXECUTOR.awaitTermination(100000, TimeUnit.DAYS);
		}
		
		long end = System.nanoTime();
		
		System.out.println("time: "+((end - start) / 1000000)+"ms");
	}
	
	private static void copyFile(File from, File to) throws Exception {
		Files.write(to.toPath(), Files.readAllBytes(from.toPath()), StandardOpenOption.CREATE_NEW);
	}
	
	private static void convertAnvilFile(File from, File to) throws Exception {
		RandomAccessFile src = new RandomAccessFile(from, "r");
		RandomAccessFile dst = new RandomAccessFile(to, "rw");
		
		int nextDstSector = 2;
		
		for(int chunkIndex = 0; chunkIndex < 1024; chunkIndex++) {
			
			//int chunkRelX = chunkIndex & 31;
			//int chunkRelZ = (chunkIndex >> 5) & 31;
			//System.out.println("chunk "+chunkRelX+","+chunkRelZ);
			if((chunkIndex & 15) == 0)
				System.out.print(".");
			
			// copy timestamp
			src.seek(chunkIndex*4 + 4096);
			dst.seek(chunkIndex*4 + 4096);
			dst.writeInt(src.readInt());
			
			src.seek(chunkIndex*4);
			int packedLocation = src.readInt();
			
			if(packedLocation == 0) {
				dst.seek(chunkIndex*4);
				dst.writeInt(0);
				continue;
			}
			
			int startPos = (packedLocation >> 8) * 4096;
			
			src.seek(startPos);
			
			int usedSpace = src.readInt();
			
			int compType = src.readByte();
			if(compType != 2)
				throw new IOException("unhandled compression type "+compType+" at chunk index "+chunkIndex);
				
			
			byte[] chunkData = new byte[usedSpace-1];
			src.readFully(chunkData);
			
			CompoundTag tag = NbtIo.read(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(chunkData))));
			
			convertNBT(tag);
			convertBlocks(tag);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dOut = new DataOutputStream(new DeflaterOutputStream(baos));
			NbtIo.write(tag, dOut);
			dOut.close();
			chunkData = baos.toByteArray();
			
			int nDstSectors = (chunkData.length + 5 + 4095) / 4096;
			if(nDstSectors > 255)
				throw new Exception("Chunk "+chunkIndex+" won't fit in output file! Length in bytes: "+chunkData.length+". Length in sectors: "+nDstSectors+". Maximum is 255 sectors.");
			int dstSector = nextDstSector;
			nextDstSector += nDstSectors;
			int dstEndPos = nextDstSector * 4096;
			
			if(dst.length() < dstEndPos)
				dst.setLength(dstEndPos);
			dst.seek(dstSector * 4096);
			dst.writeInt(chunkData.length + 1);
			dst.writeByte(2); // compression type
			dst.write(chunkData);
			
			// write location
			dst.seek(chunkIndex * 4);
			dst.writeInt((dstSector << 8) | nDstSectors);
			
			if(chunkData.length < 10)
				throw new RuntimeException(chunkData.length+" "+nDstSectors+" "+dstSector);
		}
		System.out.println();
		
		src.close();
		dst.close();
	}
	
	private static CompoundTag readNBTGzipped(File file) throws Exception {
		FileInputStream fIn = new FileInputStream(file);
		GZIPInputStream gzIn;
		try {
			gzIn = new GZIPInputStream(fIn);
		} catch(ZipException e) {
			fIn.close();
			return null;
		} catch(EOFException e) {
			fIn.close();
			return null;
		}
		
		DataInputStream dIn = new DataInputStream(gzIn);
		CompoundTag tag = NbtIo.read(dIn);
		dIn.close();
		return tag;
	}
	
	private static void convertNBTFile(File from, File to) throws Exception {
		CompoundTag root;
		
		boolean gzipped;
		
		root = readNBTGzipped(from);
		if(root != null) {
			gzipped = true;
			System.out.println("nbt-gzipped");
		} else {
			gzipped = false;
			try {
				root = NbtIo.read(from);
				System.out.println("nbt-plain");
			} catch(IOException e) {
				System.out.println("unknown");
				copyFile(from, to);
				return;
			}
		}
		
		convertNBT(root);
		
		if(gzipped) {
			DataOutputStream dOut = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(to)));
			NbtIo.write(root, dOut);
			dOut.close();
		} else
			NbtIo.write(root, to);
	}
	
	private static int getNibble(byte[] data, int index) {
		byte b = data[index >> 1];
		return ((index & 1) != 0 ? (b >> 4) : b) & 15; 
	}
	
	private static void setNibble(byte[] data, int index, int value) {
		if((index & 1) != 0)
			data[index >> 1] = (byte)((data[index >> 1] & 0x0F) | (value << 4));
		else
			data[index >> 1] = (byte)((data[index >> 1] & 0xF0) | (value & 15));
	}
	
	private static void convertBlocks(CompoundTag root) throws Exception {
		ListTag<CompoundTag> Sections = (ListTag<CompoundTag>)root.getCompound("Level").getList("Sections");
		int chunkX = root.getCompound("Level").getInt("xPos");
		int chunkZ = root.getCompound("Level").getInt("zPos");
		for(int k = 0; k < Sections.size(); k++) {
			CompoundTag section = Sections.get(k);
			
			int sectionY = section.getByte("Y") * 16;
			
			byte[] blocks = section.getByteArray("Blocks");
			byte[] add = section.getByteArray("Add");
			byte[] data = section.getByteArray("Data");
			
			if(add.length == 0)
				add = new byte[2048];
			
			boolean printed = false;

			for(int i = 0; i < 4096; i++) {
				int blockID = (blocks[i] & 255) | (getNibble(add, i) << 8);
				int meta = getNibble(data, i);
				
				//if(blockID >= 128 && blockID < 150) {
					//System.out.println(blockID+":"+meta+" "+getNibble(add, i)+" "+i+" "+add[i>>1]+" "+((i&15) + chunkX*16)+","+((i>>8) + sectionY)+","+(((i>>4)&15) + chunkZ*16)+" "+chunkX+" "+chunkZ);
				//}
				
				IdSpec _new = lookupBlock(blockID, meta);
				if(_new != null) {
					//System.out.println(blockID+":"+meta+" matched something");
					blocks[i] = (byte)_new.id;
					setNibble(add, i, _new.id >> 8);
					if(_new.meta != IdSpec.WILDCARD_META)
						setNibble(data, i, _new.meta);
				}
			}
			
			if(printed)
				System.out.println(java.util.Arrays.toString(blocks)+"\n"+java.util.Arrays.toString(add));
			
			int i;
			for(i = 0; i < 2048 && add[i] == 0; i++);
			
			if(i == 2048)
				// no non-zero entries in Add; remove it
				section.removeTag("Add");
			else
				section.putByteArray("Add", add);
		}
	}
	
	private static void convertNBT(Tag root) throws Exception {
		if(root instanceof CompoundTag) {
			CompoundTag ct = (CompoundTag)root;
			for(Tag t : ct.getAllTags())
				convertNBT(t);
			
			if(ct.contains("id") && ct.contains("Damage") && ct.contains("Count")) {
				int meta = ct.getShort("Damage");
				int id = ct.getShort("id");
				
				IdSpec _new = lookupItem(id, meta);
				if(_new != null) {
					ct.putShort("id", (short)_new.id);
					if(_new.meta != IdSpec.WILDCARD_META)
						ct.putShort("Damage", (short)_new.meta);
				}
			}
			
		} else if(root instanceof ListTag) {
			ListTag<? extends Tag> lt = (ListTag<? extends Tag>)root;
			for(int k = 0; k < lt.size(); k++)
				convertNBT(lt.get(k));
		}
	}
}
