package VASSAL.counters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.Gateway;
import VASSAL.build.widget.PieceSlot;

public class DumpCounters {

	private static final String SEP = ":";

	public static void process(AbstractBuildable module) {
		for (Buildable c : Gateway.getBuildComponents(module)) {
			if(c instanceof PieceSlot) {
				PieceSlot slot = (PieceSlot)c;
				List<String> list = process(slot.getPiece());
				String s = toString(list);
				System.out.println(s);
			}else if(c instanceof AbstractBuildable){
				process((AbstractBuildable) c);
			}
		}
	}

	private static String toString(List<String> list) {
		if(list.size() == 1){
			return list.get(0)+SEP;
		}else if(list.size() == 2){
			return list.get(0)+SEP+list.get(1);
		}else{
			return list.toString();
		}
	}

	private static List<String> process(GamePiece p) {
		if(p instanceof Embellishment) {
			Embellishment e = (Embellishment) p;
			String[] imageName = e.imageName;
			ArrayList<String> result = new ArrayList<String>();
			result.addAll(process(e.getInner()));
			for (String string : imageName) {
				if(string != null && !string.trim().isEmpty()) {
					result.add(string);
				}
			}
			return result;
		}else if(p instanceof Decorator) {
			Decorator d = (Decorator) p;
			return process(d.getInner());
		}else if(p instanceof BasicPiece){
			BasicPiece bp = (BasicPiece) p;
			return Arrays.asList(bp.imageName);
		}else{
			System.err.println(p);
			return Collections.emptyList(); 
		}
	}

}
