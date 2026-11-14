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

func TestNormalizePortalQueryISO88591(t *testing.T) {
	mgr := NewManager("https://cdseducacional.com.br", nil)
	got := mgr.normalizeURL("/ambiente_aluno/relatorios/boletim_detalhado.asp?ra=1234567&nomealu=ALUNO EXEMPLO DA SILVA&nomecur=Bacharelado em Engenharia de Computação - Noturno&ano=2026&sem=2&grade=1928&anograde=2025&mesgrade=1&codigoMatricula=999999")
	want := "https://cdseducacional.com.br/ambiente_aluno/relatorios/boletim_detalhado.asp?ra=1234567&nomealu=ALUNO%20EXEMPLO%20DA%20SILVA&nomecur=Bacharelado%20em%20Engenharia%20de%20Computa%E7%E3o%20-%20Noturno&ano=2026&sem=2&grade=1928&anograde=2025&mesgrade=1&codigoMatricula=999999"
	if got != want {
		t.Fatalf("unexpected encoded URL:\n got: %s\nwant: %s", got, want)
	}
}

func TestParseTranscriptWithDashedRowsAndMultipleTables(t *testing.T) {
	html := `
<html><body>
<table class="texto">
  <tr><td>-----------------------------------</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td></tr>
  <tr><td>Disciplina</td><td>Ano</td><td>Exame</td><td>Média</td><td>CH</td><td>FA</td><td>Freq. (%)</td><td>Sit.</td></tr>
  <tr><td>-----------------------------------</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td></tr>
  <tr>
    <td><a href="relatorio_dadosdisciplinas.asp?grade=1928&amp;discgrad=53457">19280105 - INTRODUÇÃO À ENGENHARIA DE COMPUTAÇÃO</a></td>
    <td>1 / 2026</td><td></td><td>7,00</td><td>20</td><td>0</td><td>100,00</td><td>A</td>
  </tr>
  <tr><td>-----------------------------------</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td></tr>
  <tr>
    <td>19280403 - ELETRÔNICA</td>
    <td>2 / 2026</td><td></td><td>5,00</td><td>60</td><td>6</td><td>90,00</td><td>C</td>
  </tr>
</table>
<table class="texto">
  <tr><td>-----------------------------------</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td></tr>
  <tr><td>Disciplina</td><td>Ano</td><td>Exame</td><td>Média</td><td>CH</td><td>FA</td><td>Freq. (%)</td><td>Sit.</td></tr>
  <tr><td>-----------------------------------</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td></tr>
  <tr>
    <td>19280501 - ÉTICA E CIDADANIA</td>
    <td>/</td><td></td><td></td><td>60</td><td>0</td><td>100,00</td><td></td>
  </tr>
</table>
</body></html>`

	items, err := ParseTranscript(html)
	if err != nil {
		t.Fatalf("parse transcript: %v", err)
	}
	if len(items) != 3 {
		t.Fatalf("expected 3 items across 2 tables, got %d: %+v", len(items), items)
	}
	if items[0].SyllabusURL != "/modulo_matricula/relatorios/relatorio_dadosdisciplinas.asp?grade=1928&discgrad=53457" {
		t.Fatalf("unexpected syllabus url: %s", items[0].SyllabusURL)
	}
	if items[0].StatusLabel != "Aprovado" || items[1].StatusLabel != "Cursando" {
		t.Fatalf("unexpected status labels: %+v %+v", items[0], items[1])
	}
}

func TestParseCurriculumCombinedDisciplineColumn(t *testing.T) {
	html := `
<html><body>
<table>
  <tr><td colspan="8">1° Período</td></tr>
  <tr><td>Disciplina</td><td>P.R.</td><td>T.E.D.</td><td>A.M.T.</td><td>A.E.</td><td>A.C.</td><td>E.S.</td><td>Pré Requisito</td></tr>
  <tr>
    <td>19280102 - ALGORITMOS E PROGRAMAÇÃO DE COMPUTADORES - 60 horas</td>
    <td>50</td><td>10</td><td>0</td><td>0</td><td>0</td><td>0</td><td>-</td>
  </tr>
</table>
</body></html>`

	items, err := ParseCurriculum(html)
	if err != nil {
		t.Fatalf("parse curriculum: %v", err)
	}
	if len(items) != 1 {
		t.Fatalf("expected 1 curriculum item, got %d", len(items))
	}
	if items[0].Period != "1° Período" || items[0].Code != "19280102" || items[0].Hours != "60" {
		t.Fatalf("unexpected curriculum item: %+v", items[0])
	}
}
