package com.example.badukanalyzer.service;

import com.example.badukanalyzer.converter.SgfConverter;
import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.parser.GibParser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class SgfService {

    private final GibParser gibParser = new GibParser();
    private final SgfConverter sgfConverter = new SgfConverter();

    public String convertGibFile(String filePath) throws IOException {
        List<Move> moves = gibParser.parse(filePath);
        return sgfConverter.convert(moves);
    }

    public String movesToSgf(List<Move> moves) {
        return sgfConverter.convert(moves);
    }
}
