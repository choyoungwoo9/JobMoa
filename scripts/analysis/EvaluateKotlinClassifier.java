// 저장 스냅샷을 실제 제품 Kotlin 분류기에 통과시키는 오프라인 평가 도구. DB·외부 요청 없음.
import com.youngwoo.jobmoa.classification.infrastructure.RulePostingClassifier;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
public class EvaluateKotlinClassifier {
 public static void main(String[] args) throws Exception {
  var mapper=new JsonMapper(); var classifier=new RulePostingClassifier(); var output=new ArrayList<Map<String,Object>>();
  for(var line:Files.readAllLines(Path.of(args[0]))) {
   if(line.isBlank())continue; var input=mapper.readTree(line);
   var result=classifier.classify(input.get("title").asString(),input.get("body").asString());
   var row=new LinkedHashMap<String,Object>(); row.put("id",input.get("id").asLong());row.put("categories",result.getCategories());row.put("parserMode",result.getDetails().getParserMode());row.put("evidence",result.getDetails().getEvidence());output.add(row);
  }
  mapper.writeValue(Path.of(args[1]).toFile(),output);
 }
}
