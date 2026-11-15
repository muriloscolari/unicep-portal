package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os/signal"
	"syscall"
	"time"

	"unicep-portal/internal/config"
	"unicep-portal/internal/crypto"
	"unicep-portal/internal/d2l"
	"unicep-portal/internal/httpapi"
	"unicep-portal/internal/poller"
	"unicep-portal/internal/portal"
	"unicep-portal/internal/storage"
)

func main() {
	cfg := config.Load()

	store, err := storage.Open(cfg.DataDir)
	if err != nil {
		log.Fatalf("open storage: %v", err)
	}
	defer store.Close()

	cry := crypto.NewService(cfg.AppSecret)

	pm := portal.NewManager(cfg.PortalBaseURL, func(ra string) (string, string, error) {
		u, err := store.GetUser(ra)
		if err != nil {
			return "", "", err
		}
		if u == nil {
			return "", "", portal.ErrSessionExpired
		}
		pass, err := cry.Decrypt(u.EncryptedPassword)
		if err != nil {
			return "", "", err
		}
		return pass, u.Perfil, nil
	})

	dm := d2l.NewManager(cfg.D2LBaseURL, func(ra string) (string, error) {
		u, err := store.GetUser(ra)
		if err != nil {
			return "", err
		}
		if u == nil {
			return "", d2l.ErrSessionExpired
		}
		return cry.Decrypt(u.EncryptedPassword)
	})

	worker := poller.NewWorker(store, cry, pm, dm, time.Duration(cfg.PollIntervalMinutes)*time.Minute, cfg.NotifyAbsences)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	go worker.Start(ctx)

	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           httpapi.NewServer(cfg, store, cry, pm, worker, dm),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      45 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    32 << 10,
	}

	go func() {
		log.Printf("UNICEP Portal BFF listening on :%s (poll every %dm)", cfg.Port, cfg.PollIntervalMinutes)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("http server: %v", err)
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutdownCtx)
}
