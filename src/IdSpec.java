
public final class IdSpec {
	public final int id;
	public final int meta;
	
	public final static int WILDCARD_META = Integer.MAX_VALUE;
	
	public IdSpec(int id, int meta) {
		this.id = id;
		this.meta = meta;
	}
	
	@Override
	public String toString() {
		if(meta == WILDCARD_META)
			return id+":*";
		else
			return id+":"+meta;
	}
	
	public boolean matches(int id, int meta) {
		return (this.id == id) && (this.meta == WILDCARD_META || this.meta == meta);
	}
	
	public static IdSpec parse(String s) {
		String[] parts = s.split(":");
		if(parts.length == 1)
			return new IdSpec(Integer.parseInt(parts[0]), WILDCARD_META);
		else if(parts.length == 2)
			return new IdSpec(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
		else
			throw new RuntimeException("Invalid idspec: "+s);
	}
	
	public void assertBlock(boolean allowWildcard) throws Exception {
		if(id < 0 || id > 4095 || ((meta < 0 || meta > 15) && (!allowWildcard || meta != WILDCARD_META)))
			throw new Exception("invalid block: "+this);
	}
	
	public void assertItem(boolean allowWildcard) throws Exception {
		if(id < 0 || id > 31999 || ((meta < 0 || meta > 32767) && (!allowWildcard || meta != WILDCARD_META)))
			throw new Exception("invalid item: "+this);
	}
}
