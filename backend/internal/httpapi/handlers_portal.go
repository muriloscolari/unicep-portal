package httpapi

import (
	"errors"
	"net/http"
	"net/url"
	"strings"
	"time"

	"unicep-portal/internal/portal"
	"unicep-portal/internal/storage"
)

func (s *Server) handleDashboard(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	homeHTML, info, nacOpen, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/ambiente_estudante.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	summary, _ := portal.ParseDashboard(homeHTML, nacOpen)

	gradesSel, _, _, _ := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/consulta_notas_primeira.asp")
	gradePeriods, _ := portal.ParsePeriodOptions(gradesSel, "/ambiente_aluno/relatorios/consulta_notas.asp?matr=")

	reportSel, _, _, _ := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/boletim_geral.asp")
	reportPeriods, _ := portal.ParsePeriodOptions(reportSel, "/ambiente_aluno/relatorios/boletim_detalhado.asp")

	currentGrades := []portal.PartialGradeItem{}
	if len(gradePeriods) > 0 {
		if gHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, gradePeriods[0].Query); err == nil {
			if parsed, err := portal.ParseGrades(gHTML); err == nil && parsed != nil {
				currentGrades = parsed
			}
		}
	}

	currentReport := portal.ReportCardResponse{Items: []portal.ReportCardItem{}}
	if len(reportPeriods) > 0 {
		if rHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, reportPeriods[0].Query); err == nil {
			if parsed, err := portal.ParseReportCard(rHTML, reportPeriods[0].ID); err == nil {
				currentReport = parsed
			}
		}
	}
	if currentReport.Items == nil {
		currentReport.Items = []portal.ReportCardItem{}
	}

	unread, _ := s.store.ListNotifications(claims.RA, true, 50)
	if gradePeriods == nil {
		gradePeriods = []portal.PeriodOption{}
	}
	if reportPeriods == nil {
		reportPeriods = []portal.PeriodOption{}
	}
	if unread == nil {
		unread = []storage.Notification{}
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"student":             info,
		"summary":             summary,
		"gradePeriods":        gradePeriods,
		"reportPeriods":       reportPeriods,
		"currentGrades":       currentGrades,
		"currentReport":       currentReport,
		"unreadNotifications": unread,
	})
}

func (s *Server) handlePeriods(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	gradesSel, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/consulta_notas_primeira.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	gradePeriods, _ := portal.ParsePeriodOptions(gradesSel, "/ambiente_aluno/relatorios/consulta_notas.asp?matr=")

	reportSel, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/boletim_geral.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	reportPeriods, _ := portal.ParsePeriodOptions(reportSel, "/ambiente_aluno/relatorios/boletim_detalhado.asp")

	writeJSON(w, http.StatusOK, map[string]any{
		"gradePeriods":  gradePeriods,
		"reportPeriods": reportPeriods,
	})
}

func (s *Server) handleGrades(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	period := strings.TrimSpace(r.URL.Query().Get("period"))
	if period == "" {
		gradesSel, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/consulta_notas_primeira.asp")
		if err != nil {
			s.writePortalErr(w, err)
			return
		}
		periods, _ := portal.ParsePeriodOptions(gradesSel, "")
		if len(periods) == 0 {
			writeJSON(w, http.StatusOK, map[string]any{"period": "", "items": []portal.PartialGradeItem{}})
			return
		}
		period = periods[0].ID
	} else if !isSafeParam(period, 128) {
		writeError(w, http.StatusBadRequest, "período inválido")
		return
	}

	target := "/ambiente_aluno/relatorios/consulta_notas.asp?matr=" + url.QueryEscape(period)
	html, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, target)
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	items, err := portal.ParseGrades(html)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao interpretar notas")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"period": period,
		"items":  items,
	})
}

func (s *Server) handleReport(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	periodQuery := strings.TrimSpace(r.URL.Query().Get("period"))
	if periodQuery == "" {
		reportSel, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/boletim_geral.asp")
		if err != nil {
			s.writePortalErr(w, err)
			return
		}
		periods, _ := portal.ParsePeriodOptions(reportSel, "")
		if len(periods) == 0 {
			writeJSON(w, http.StatusOK, portal.ReportCardResponse{Items: []portal.ReportCardItem{}})
			return
		}
		periodQuery = periods[0].ID
	}
	if !strings.HasPrefix(periodQuery, "?") {
		periodQuery = "?" + periodQuery
	}
	if !isSafeQueryString(periodQuery[1:], 1024) {
		writeError(w, http.StatusBadRequest, "parâmetro de boletim inválido")
		return
	}

	target := "/ambiente_aluno/relatorios/boletim_detalhado.asp" + periodQuery
	html, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, target)
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	resp, err := portal.ParseReportCard(html, periodQuery)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao interpretar boletim")
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleSchedule(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	const baseSchedulePath = "/ambiente_aluno/relatorios/quadro_horarios.asp"
	html, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, baseSchedulePath)
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	sched, err := portal.ParseSchedule(html)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao interpretar horários")
		return
	}
	if len(sched.Rows) == 0 {
		if action, method, formMap, found := portal.ParseScheduleForm(html); found {
			targetPath := baseSchedulePath
			if action != "" {
				if strings.HasPrefix(action, "/") {
					targetPath = action
				} else if !strings.HasPrefix(action, "http") {
					targetPath = "/ambiente_aluno/relatorios/" + strings.TrimPrefix(action, "./")
				}
			}
			semOrder := []string{"2", "1"}
			if time.Now().Month() < time.July {
				semOrder = []string{"1", "2"}
			}
			for _, sem := range semOrder {
				vals := url.Values{}
				for k, v := range formMap {
					vals.Set(k, v)
				}
				if _, hasSem := formMap["cboSemestre"]; hasSem {
					vals.Set("cboSemestre", sem)
				}
				var html2 string
				var err2 error
				if strings.EqualFold(method, "POST") {
					html2, _, _, err2 = s.portal.PostFormHTML(r.Context(), claims.RA, targetPath, vals)
				} else {
					sep := "?"
					if strings.Contains(targetPath, "?") {
						sep = "&"
					}
					html2, _, _, err2 = s.portal.FetchHTML(r.Context(), claims.RA, targetPath+sep+vals.Encode())
				}
				if err2 == nil {
					if parsed2, errParse := portal.ParseSchedule(html2); errParse == nil && len(parsed2.Rows) > 0 {
						sched = parsed2
						break
					}
				}
			}
		}
	}
	writeJSON(w, http.StatusOK, sched)
}

func (s *Server) handleCurriculum(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	transcriptHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/modulo_matricula/relatorios/ficha_individual_todas_semDuplicidade.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	transcript, _ := portal.ParseTranscript(transcriptHTML)

	gradeID := strings.TrimSpace(r.URL.Query().Get("gradeId"))
	if gradeID != "" && !isSafeParam(gradeID, 64) {
		writeError(w, http.StatusBadRequest, "gradeId inválido")
		return
	}
	if gradeID == "" {
		if homeHTML, _, nacOpen, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/ambiente_estudante.asp"); err == nil {
			if summary, err := portal.ParseDashboard(homeHTML, nacOpen); err == nil {
				gradeID = summary.GradeID
			}
		}
	}
	matrix := []portal.CurriculumItem{}
	if gradeID != "" {
		if mHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/modulo_matricula/relatorios/relatorio_matriz_curricular_apartir_2025_unicep.asp?grade="+url.QueryEscape(gradeID)); err == nil {
			if parsed, err := portal.ParseCurriculum(mHTML); err == nil && parsed != nil {
				matrix = parsed
			}
		}
	}
	if transcript == nil {
		transcript = []portal.TranscriptItem{}
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"transcript": transcript,
		"matrix":     matrix,
		"gradeId":    gradeID,
	})
}

func (s *Server) handleFinance(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	html, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/listaDebito.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	items, err := portal.ParseTuition(html)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao interpretar mensalidades")
		return
	}
	links := []portal.LinkItem{
		{Title: "Declaração de Pagamento (IR)", Path: "/financeiro/relatorios/declaracao_imposto_renda.asp", Category: "Financeiro", Proxy: true},
		{Title: "Pagamento de Mensalidade (Portal)", Path: s.cfg.PortalBaseURL + "/ambiente_aluno/pagamentoMensalidade.asp", Category: "Financeiro", Proxy: false},
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"installments": items,
		"links":        links,
	})
}

func (s *Server) handleServices(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	docsHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/documentos_institucionais.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	docs, _ := portal.ParseInstitutionalDocs(docsHTML)

	shortcuts := []portal.LinkItem{
		{Title: "Localização de Salas (PDF)", Path: "/ambiente_aluno/Localizacao_salas_v1.pdf", Category: "Campus", Proxy: true},
		{Title: "Impressão de Documentos On-line", Path: s.cfg.PortalBaseURL + "/modulo_matricula/relatorios/docs_online/gerarDocs.php", Category: "Serviços", Proxy: false},
		{Title: "Requerimentos Diversos", Path: s.cfg.PortalBaseURL + "/solicitacaoDocumentos/solicitacaoDocumento.asp", Category: "Serviços", Proxy: false},
		{Title: "Atividades Complementares", Path: s.cfg.PortalBaseURL + "/ambiente_coordenador_novo/controle_atividades_complementares/aluno.asp", Category: "Serviços", Proxy: false},
		{Title: "Banco de Empregos", Path: s.cfg.PortalBaseURL + "/ambiente_aluno/vagas.asp", Category: "Carreira", Proxy: false},
		{Title: "Avaliação Institucional (NAC)", Path: s.cfg.PortalBaseURL + "/MVCGraduacao/View/avaliacaoInstitucionalDis.php", Category: "Institucional", Proxy: false},
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"shortcuts":         shortcuts,
		"institutionalDocs": docs,
	})
}

func (s *Server) handleAvaSSO(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	if !s.actionRALim.Allow("ava:" + claims.RA) {
		writeError(w, http.StatusTooManyRequests, "muitas solicitações; aguarde alguns segundos")
		return
	}
	raw, _, _, _, err := s.portal.FetchRaw(r.Context(), claims.RA, "/d2l_login.php?ra="+url.QueryEscape(claims.RA)+"&nocache="+url.QueryEscape(time.Now().Format("150405")))
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	target := strings.TrimSpace(string(raw))
	if !isSafeExternalHTTPSURL(target) {
		writeError(w, http.StatusBadGateway, "não foi possível gerar link SSO seguro do UnicepVirtual")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"url": target})
}

func (s *Server) writePortalErr(w http.ResponseWriter, err error) {
	if errors.Is(err, portal.ErrInvalidCredentials) || errors.Is(err, portal.ErrSessionExpired) {
		writeError(w, http.StatusUnauthorized, "sessão expirada no portal da UNICEP; faça login novamente")
		return
	}
	writeError(w, http.StatusBadGateway, "erro ao consultar o portal da UNICEP")
}
