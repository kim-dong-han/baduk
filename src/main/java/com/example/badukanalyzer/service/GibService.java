package com.example.badukanalyzer.service;

import com.example.badukanalyzer.converter.SgfConverter;
import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.parser.GibParser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class GibService {
    public void saveSgf(String sgf) throws IOException {

        Files.writeString(
                Path.of("C:/KataGo/output.sgf"),
                sgf
        );
    }

    public String convertGibToSgf(String filePath)
            throws IOException {

        GibParser parser = new GibParser();

        List<Move> moves =
                parser.parse(filePath);

        SgfConverter converter =
                new SgfConverter();

        return converter.convert(moves);
    }
}