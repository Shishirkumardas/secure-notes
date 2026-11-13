const DEFAULT_LIMIT = 10;
const MAX_LIMIT = 50;
const MAX_PAGE = 100000;

function parsePagination(query) {
  const pageRaw = Number.parseInt(query.page, 10);
  const limitRaw = Number.parseInt(query.limit, 10);
  let page = Number.isInteger(pageRaw) && pageRaw > 0 ? pageRaw : 1;
  let limit = Number.isInteger(limitRaw) && limitRaw > 0 ? limitRaw : DEFAULT_LIMIT;
  if (page > MAX_PAGE) page = MAX_PAGE;
  if (limit > MAX_LIMIT) limit = MAX_LIMIT;
  return { page, limit, skip: (page - 1) * limit };
}

function paginationMeta(page, limit, total) {
  return {
    page,
    limit,
    total,
    totalPages: total === 0 ? 0 : Math.ceil(total / limit),
  };
}

module.exports = { parsePagination, paginationMeta, DEFAULT_LIMIT, MAX_LIMIT };
