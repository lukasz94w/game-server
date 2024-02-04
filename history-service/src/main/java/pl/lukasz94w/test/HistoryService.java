package pl.lukasz94w.test;

import org.springframework.stereotype.Service;

@Service
public class HistoryService {

    private final HistoryRepository historyRepository;

    public HistoryService(HistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public void save(String name) {
        historyRepository.save(new HistoryEntity());
    }

    public HistoryEntity findByName(String name) {
        return historyRepository.findByName(name);
    }
}
