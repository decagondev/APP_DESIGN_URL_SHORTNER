-module(url_shortener).
-export([start/0]).

-include_lib("cowboy/include/cowboy.hrl").

start() ->
    {ok, _} = cowboy:start_http(http_listener, 100, [{port, 8080}], []),
    io:format("Server started on port 8080~n"),
    cowboy:set_env(http_listener, dispatch, Dispatch),
    ok = cowboy:start_clear(http_listener).

Dispatch = cowboy_router:compile([
    {'_', [
        {"/", cowboy_static, {file, <<"public/index.html">>}},
        {"/shorten", shortener_handler, []},
        {"/{ShortCode}", redirect_handler, []},
        {"/analytics/{ShortCode}", analytics_handler, []}
    ]}
]).

shortener_handler(Req, State) ->
    Body = cowboy_req:body(Req),
    {ok, Req1} = cowboy_req:read_body(Req),
    case cowboy_req:method(Req) of
        << "POST" >> ->
            {ok, OriginalUrl} = proplists:get_value(<<"original_url">>, cowboy_req:parse_qs(Body)),
            ShortCode = generate_unique_short_code(OriginalUrl),
            save_mapping(ShortCode, OriginalUrl),
            ShortURL = lists:concat(["http://localhost:8080/", ShortCode]),
            cowboy_req:reply(201, #{<<"content-type">> => <<"application/json">>}, <<"{\"short_url\":\"", ShortURL, "\"}">>, Req1);
        _ ->
            cowboy_req:reply(405, #{}, <<"Method Not Allowed">>, Req1)
    end.

redirect_handler(Req, State) ->
    ShortCode = cowboy_req:binding(<<"ShortCode">>, Req),
    case get_original_url(ShortCode) of
        {ok, OriginalUrl} ->
            log_analytics(ShortCode, Req),
            cowboy_req:reply(302, #{<<"location">> => OriginalUrl}, <<>>, Req);
        error ->
            cowboy_req:reply(404, #{}, <<"Not Found">>, Req)
    end.

analytics_handler(Req, State) ->
    ShortCode = cowboy_req:binding(<<"ShortCode">>, Req),
    case get_analytics(ShortCode) of
        {ok, AnalyticsData} ->
            cowboy_req:reply(200, #{<<"content-type">> => <<"application/json">>}, AnalyticsData, Req);
        error ->
            cowboy_req:reply(404, #{}, <<"Not Found">>, Req)
    end.

generate_short_code(OriginalUrl) ->
    ShortCode = lists:sublist(crypto:hash(sha256, OriginalUrl), 1, 8),
    lists:flatten(ShortCode).

check_collision(ShortCode) ->
    ets:lookup(url_mappings, ShortCode) /= [].

generate_unique_short_code(OriginalUrl) ->
    generate_unique_short_code(OriginalUrl, 0).

generate_unique_short_code(_, 10) ->
    throw(error);

generate_unique_short_code(OriginalUrl, Tries) ->
    ShortCode = generate_short_code(OriginalUrl ++ integer_to_list(Tries)),
    case check_collision(ShortCode) of
        true -> generate_unique_short_code(OriginalUrl, Tries + 1);
        false -> ShortCode
    end.

save_mapping(ShortCode, OriginalUrl) ->
    ets:insert(url_mappings, {ShortCode, OriginalUrl}).

get_original_url(ShortCode) ->
    case ets:lookup(url_mappings, ShortCode) of
        [] -> error;
        [{_, OriginalUrl}] -> {ok, OriginalUrl}
    end.

log_analytics(ShortCode, Req) ->
    Timestamp = io_lib:format("~s", [httpd_util:rfc1123_date()], []),
    IPAddress = inet_parse:ntoa(cowboy_req:peer(Req)),
    ets:insert(analytics_data, {ShortCode, Timestamp, IPAddress}).

get_analytics(ShortCode) ->
    case ets:lookup(analytics_data, ShortCode) of
        [] -> error;
        Data -> {ok, format_analytics_data(Data)}
    end.

format_analytics_data(Data) ->
    [io_lib:format("{\"timestamp\":\"~s\",\"ip_address\":\"~s\"}", [Timestamp, IPAddress]) || {_, Timestamp, IPAddress} <- Data].
