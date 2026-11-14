package portal

import (
	"testing"

	"golang.org/x/text/encoding/charmap"
)

func TestDecodeAndParseGrades(t *testing.T) {
	utf8Sample := `
<html><head><meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"></head>
<body>
<form name="form1"><table><tr><td>
<table class="topoesq">
  <tr class="texto">
    <td>Código</td><td>Nome da Disciplina</td>
    <td>P1</td><td>P2</td><td>P3</td><td>P4</td>
    <td>T1</td><td>T2</td><td>T3</td><td>T4</td>
    <td>Média</td>
    <td>%P1</td><td>%P2</td><td>%P3</td><td>%P4</td>
    <td>%T1</td><td>%T2</td><td>%T3</td><td>%T4</td>
  </tr>
  <tr class="texto2">
    <td class="basedir"><div>19280403</div></td>
    <td class="basedir"><div>ESTRUTURAS DE DADOS E ALGORITMOS</div></td>
    <td class="basedir"><div> 8,5 </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> 10 </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div><span style="color:red">5,00</span></div></td>
    <td class="basedir"><div> 70 </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> 30 </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> - </div></td>
    <td class="basedir"><div> - </div></td>
  </tr>
</table>
</td></tr></table></form>
</body></html>`

	winBytes, err := charmap.Windows1252.NewEncoder().Bytes([]byte(utf8Sample))
	if err != nil {
		t.Fatalf("encode win1252: %v", err)
	}
	decoded, err := DecodePortalHTML(winBytes)
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	items, err := ParseGrades(decoded)
	if err != nil {
		t.Fatalf("parse grades: %v", err)
	}
	if len(items) != 1 {
		t.Fatalf("expected 1 item, got %d", len(items))
	}
	g := items[0]
	if g.Code != "19280403" || g.Name != "ESTRUTURAS DE DADOS E ALGORITMOS" {
		t.Fatalf("unexpected identity: %+v", g)
	}
	if g.Exams[0].Display != "8,5" || g.Exams[0].Value == nil || *g.Exams[0].Value != 8.5 {
		t.Fatalf("unexpected P1: %+v", g.Exams[0])
	}
	if g.Exams[1].Display != "" || g.Exams[1].Value != nil {
		t.Fatalf("expected empty P2, got %+v", g.Exams[1])
	}
	if g.Works[0].Display != "10" || g.ExamWeights[0].Display != "70" {
		t.Fatalf("unexpected T1/weight: %+v %+v", g.Works[0], g.ExamWeights[0])
	}
	if !g.BelowPassing || g.Average.Display != "5,00" || *g.Average.Value != 5.0 {
		t.Fatalf("unexpected average: %+v below=%v", g.Average, g.BelowPassing)
	}
}

func TestParseReportCard(t *testing.T) {
	html := `
<html><body>
<table class="topoesq">
  <tr class="texto">
    <td>Código</td><td>Nome da Disciplina</td><td>Dt.Atual.</td><td>Turma</td>
    <td>MF</td><td>CH</td><td>FA</td><td>Freq. (%)</td><td>Sit.</td>
  </tr>
  <tr class="texto2">
    <td class="basedir"><div>19280401</div></td>
    <td class="basedir"><div>ARQUITETURA DE COMPUTADORES</div></td>
    <td class="basedir"><div>10/11/2026</div></td>
    <td class="basedir"><div>Turma A</div></td>
    <td class="basedir"><div>-</div></td>
    <td class="basedir"><div>80</div></td>
    <td class="basedir"><div>6</div></td>
    <td class="basedir"><div>92</div></td>
    <td class="basedir"><div>C</div></td>
  </tr>
  <tr><td colspan="9">Hora(s) de Atividade(s) Complementar(es) no Semestre: (0 h).</td></tr>
  <tr><td colspan="9">Total de Hora(s) de Atividade(s) Complementar(es) : (12 h).</td></tr>
</table>
</body></html>`

	res, err := ParseReportCard(html, "?ano=2026&sem=2")
	if err != nil {
		t.Fatalf("parse report: %v", err)
	}
	if len(res.Items) != 1 {
		t.Fatalf("expected 1 row, got %d", len(res.Items))
	}
	it := res.Items[0]
	if it.Hours != 80 || it.Absences != 6 || it.MaxAbsences != 20 || it.RemainingAbsences != 14 {
		t.Fatalf("unexpected attendance math: %+v", it)
	}
	if it.StatusLabel != "Cursando" {
		t.Fatalf("unexpected status label: %s", it.StatusLabel)
	}
	if res.SemesterComplementaryHours == "" || res.TotalComplementaryHours == "" {
		t.Fatalf("expected complementary hours parsed")
	}
}
